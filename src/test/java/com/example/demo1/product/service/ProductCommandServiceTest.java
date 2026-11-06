package com.example.demo1.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.dto.ProductUpdateResult;
import com.example.demo1.product.exception.InvalidProductImageException;
import com.example.demo1.storage.dto.FileUploadResponse;
import com.example.demo1.storage.exception.FileStorageException;
import com.example.demo1.storage.exception.InvalidFileException;
import com.example.demo1.storage.service.FileStorageService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드와 파일 정리 흐름만 검증한다. ProductService와 FileStorageService는 가짜 객체라
 * DB와 Supabase에 접속하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final ProductResponse PRODUCT =
            new ProductResponse(1L, "키보드", 30000, 5, List.of(), NOW, NOW);

    private static final MultipartFile FRONT = image("front.png");
    private static final MultipartFile SIDE = image("side.png");
    private static final FileUploadResponse FRONT_UPLOADED = uploaded("front");
    private static final FileUploadResponse SIDE_UPLOADED = uploaded("side");

    @Mock
    private ProductService productService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductCommandService productCommandService;

    @Test
    void registerUploadsFilesInOrderAndCreatesProductWithTheirUrls() {
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(fileStorageService.upload(SIDE)).thenReturn(SIDE_UPLOADED);
        ProductCreateRequest expected = new ProductCreateRequest(
                "키보드", 30000, 5, List.of(FRONT_UPLOADED.url(), SIDE_UPLOADED.url()));
        when(productService.create(expected)).thenReturn(PRODUCT);

        ProductResponse response = productCommandService.register(
                new ProductCreateRequest("키보드", 30000, 5, List.of("ignored.jpg")), List.of(FRONT, SIDE));

        assertThat(response).isEqualTo(PRODUCT);
    }

    @Test
    void registerWithoutFilesCreatesProductWithoutImages() {
        when(productService.create(new ProductCreateRequest("키보드", 30000, 5, List.of()))).thenReturn(PRODUCT);

        assertThat(productCommandService.register(new ProductCreateRequest("키보드", 30000, 5, null), null))
                .isEqualTo(PRODUCT);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void registerRejectsNonImageBeforeUploadingAnything() {
        MultipartFile text = new MockMultipartFile("files", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> productCommandService.register(
                new ProductCreateRequest("키보드", 30000, 5, null), List.of(FRONT, text)))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("note.txt");
        verifyNoInteractions(fileStorageService, productService);
    }

    @Test
    void registerRejectsFileWithoutContentTypeOrWithWildcardType() {
        MultipartFile noType = new MockMultipartFile("files", "a.png", null, new byte[] {1});
        MultipartFile wildcard = new MockMultipartFile("files", "b.png", "image/*", new byte[] {1});
        MultipartFile broken = new MockMultipartFile("files", "c.png", "not a type", new byte[] {1});
        ProductCreateRequest request = new ProductCreateRequest("키보드", 30000, 5, null);

        for (MultipartFile file : List.of(noType, wildcard, broken)) {
            assertThatThrownBy(() -> productCommandService.register(request, List.of(file)))
                    .isInstanceOf(InvalidFileException.class);
        }
        verifyNoInteractions(fileStorageService, productService);
    }

    @Test
    void registerDeletesUploadedFilesWhenSavingFails() {
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(fileStorageService.upload(SIDE)).thenReturn(SIDE_UPLOADED);
        when(productService.create(any())).thenThrow(new IllegalStateException("DB 오류"));

        assertThatThrownBy(() -> productCommandService.register(
                new ProductCreateRequest("키보드", 30000, 5, null), List.of(FRONT, SIDE)))
                .isInstanceOf(IllegalStateException.class);
        verify(fileStorageService).deleteByUrl(FRONT_UPLOADED.url());
        verify(fileStorageService).deleteByUrl(SIDE_UPLOADED.url());
    }

    @Test
    void registerDeletesAlreadyUploadedFilesWhenALaterUploadFails() {
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(fileStorageService.upload(SIDE)).thenThrow(new FileStorageException("파일 업로드에 실패했습니다.", null));

        assertThatThrownBy(() -> productCommandService.register(
                new ProductCreateRequest("키보드", 30000, 5, null), List.of(FRONT, SIDE)))
                .isInstanceOf(FileStorageException.class);
        verify(fileStorageService).deleteByUrl(FRONT_UPLOADED.url());
        verify(productService, never()).create(any());
    }

    @Test
    void cleanupFailureDoesNotHideOriginalException() {
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(productService.create(any())).thenThrow(new IllegalStateException("DB 오류"));
        doThrow(new FileStorageException("파일 삭제에 실패했습니다.", null))
                .when(fileStorageService).deleteByUrl(anyString());

        assertThatThrownBy(() -> productCommandService.register(
                new ProductCreateRequest("키보드", 30000, 5, null), List.of(FRONT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
    }

    @Test
    void updatePassesNewUrlsAndDeletesRemovedImagesAfterSuccess() {
        ProductUpdateRequest request = new ProductUpdateRequest("키보드", 30000, 5, List.of("https://old/a.png"));
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(productService.update(1L, request, List.of(FRONT_UPLOADED.url())))
                .thenReturn(new ProductUpdateResult(PRODUCT, List.of("https://old/b.png")));

        ProductResponse response = productCommandService.update(1L, request, List.of(FRONT));

        assertThat(response).isEqualTo(PRODUCT);
        verify(fileStorageService).deleteByUrl("https://old/b.png");
        verify(fileStorageService, never()).deleteByUrl(FRONT_UPLOADED.url());
    }

    @Test
    void updateSucceedsEvenIfDeletingRemovedImageFails() {
        ProductUpdateRequest request = new ProductUpdateRequest("키보드", 30000, 5, List.of());
        when(productService.update(1L, request, List.of()))
                .thenReturn(new ProductUpdateResult(PRODUCT, List.of("https://old/b.png")));
        doThrow(new FileStorageException("파일 삭제에 실패했습니다.", null))
                .when(fileStorageService).deleteByUrl("https://old/b.png");

        assertThat(productCommandService.update(1L, request, null)).isEqualTo(PRODUCT);
    }

    @Test
    void updateFailureDeletesOnlyNewlyUploadedFiles() {
        ProductUpdateRequest request = new ProductUpdateRequest("키보드", 30000, 5, List.of("https://other/x.png"));
        when(fileStorageService.upload(FRONT)).thenReturn(FRONT_UPLOADED);
        when(productService.update(eq(1L), eq(request), any()))
                .thenThrow(new InvalidProductImageException("상품에 없는 이미지입니다. url=https://other/x.png"));

        assertThatThrownBy(() -> productCommandService.update(1L, request, List.of(FRONT)))
                .isInstanceOf(InvalidProductImageException.class);
        verify(fileStorageService).deleteByUrl(FRONT_UPLOADED.url());
        verify(fileStorageService, never()).deleteByUrl("https://other/x.png");
    }

    @Test
    void updateRejectsNonImageBeforeUploadingAnything() {
        MultipartFile text = new MockMultipartFile("files", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> productCommandService.update(
                1L, new ProductUpdateRequest("키보드", 30000, 5, List.of()), List.of(text)))
                .isInstanceOf(InvalidFileException.class);
        verifyNoInteractions(fileStorageService);
        verify(productService, never()).update(anyLong(), any(), any());
    }

    private static MultipartFile image(String name) {
        return new MockMultipartFile("files", name, "image/png", new byte[] {1, 2, 3});
    }

    private static FileUploadResponse uploaded(String name) {
        String path = "2026/11/06/" + name + ".png";
        return new FileUploadResponse(path, "https://project.supabase.co/storage/v1/object/public/images/" + path);
    }
}
