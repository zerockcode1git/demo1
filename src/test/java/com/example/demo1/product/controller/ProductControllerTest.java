package com.example.demo1.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductImageResponse;
import com.example.demo1.product.dto.ProductListResponse;
import com.example.demo1.product.dto.ProductPageRequest;
import com.example.demo1.product.dto.ProductPageResponse;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.exception.InvalidProductImageException;
import com.example.demo1.product.exception.ProductNotFoundException;
import com.example.demo1.product.service.ProductCommandService;
import com.example.demo1.product.service.ProductService;
import com.example.demo1.storage.exception.FileStorageException;
import com.example.demo1.storage.exception.InvalidFileException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 계층만 실행한다. 서비스는 가짜 객체라 DB와 Supabase에 접속하지 않는다.
 * 전체 범위의 FileStorageControllerAdvice도 함께 올라온다.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final ProductResponse PRODUCT = new ProductResponse(
            1L, "키보드", 30000, 5, List.of(new ProductImageResponse("https://x/a.png", 0)), NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductService productService;

    @Test
    void createReturns201() throws Exception {
        when(productCommandService.register(eq(new ProductCreateRequest("키보드", 30000, 5, null)), anyList()))
                .thenReturn(PRODUCT);

        mockMvc.perform(multipart("/api/products")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5}"))
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.images[0].imageUrl").value("https://x/a.png"))
                .andExpect(jsonPath("$.images[0].imageIndex").value(0));
    }

    @Test
    void createWithoutFilesPassesNull() throws Exception {
        when(productCommandService.register(any(), isNull())).thenReturn(PRODUCT);

        mockMvc.perform(multipart("/api/products")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5}")))
                .andExpect(status().isCreated());
    }

    @Test
    void createValidationErrorsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(multipart("/api/products")
                        .file(productPart("{\"name\":\" \",\"price\":-1,\"stock\":-1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.name").value("상품명을 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.price").value("가격은 0 이상이어야 합니다."))
                .andExpect(jsonPath("$.fieldErrors.stock").value("재고 수량은 0 이상이어야 합니다."));

        mockMvc.perform(multipart("/api/products").file(productPart("{\"name\":\"키보드\",\"price\":30000}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stock").value("재고 수량을 입력해 주세요."));

        verifyNoInteractions(productCommandService);
    }

    @Test
    void createWithoutProductPartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/products")
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'product' 항목이 필요합니다."));
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(multipart("/api/products").file(productPart("{\"name\":")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void createWithProductPartWithoutJsonContentTypeReturns415() throws Exception {
        mockMvc.perform(multipart("/api/products").file(new MockMultipartFile(
                        "product", "", null,
                        "{\"name\":\"키보드\",\"price\":1,\"stock\":1}".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void fileErrorsAreHandledByFileStorageAdvice() throws Exception {
        when(productCommandService.register(any(), any()))
                .thenThrow(new InvalidFileException("이미지 파일만 업로드할 수 있습니다. 파일=note.txt"))
                .thenThrow(new FileStorageException("파일 업로드에 실패했습니다.", null));

        mockMvc.perform(multipart("/api/products")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이미지 파일만 업로드할 수 있습니다. 파일=note.txt"));

        mockMvc.perform(multipart("/api/products")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5}")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("파일 업로드에 실패했습니다."));
    }

    @Test
    void getOneReturnsProduct() throws Exception {
        when(productService.getOne(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("키보드"));
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(productService.getOne(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다. id=99"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
        verifyNoInteractions(productService);
    }

    @Test
    void getAllUsesDefaultsAndPassesKeyword() throws Exception {
        ProductPageResponse page = new ProductPageResponse(
                List.of(new ProductListResponse(1L, "키보드", 30000, 5, "https://x/a.png")), 1, 10, 1, 1, false);
        when(productService.getAll(new ProductPageRequest(1, 10, "키보"))).thenReturn(page);

        mockMvc.perform(get("/api/products").param("keyword", "키보"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://x/a.png"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void invalidListParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("페이지 번호는 1 이상이어야 합니다."));
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("페이지 크기는 100 이하여야 합니다."));
        mockMvc.perform(get("/api/products").param("keyword", "가".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.keyword").value("검색어는 100자 이하여야 합니다."));
        mockMvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("요청 값의 형식이 올바르지 않습니다."));
        verifyNoInteractions(productService);
    }

    @Test
    void updateReturnsProduct() throws Exception {
        ProductUpdateRequest request = new ProductUpdateRequest("키보드", 30000, 5, List.of("https://x/a.png"));
        when(productCommandService.update(eq(1L), eq(request), anyList())).thenReturn(PRODUCT);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5,"
                                + "\"imageUrls\":[\"https://x/a.png\"]}"))
                        .file(new MockMultipartFile("files", "b.png", "image/png", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateRequiresImageUrls() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.imageUrls").value("이미지 목록을 입력해 주세요."));
        verifyNoInteractions(productCommandService);
    }

    @Test
    void updateWithUnknownImageUrlReturns400() throws Exception {
        when(productCommandService.update(eq(1L), any(), any()))
                .thenThrow(new InvalidProductImageException("상품에 없는 이미지입니다. url=https://other/x.png"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/1")
                        .file(productPart("{\"name\":\"키보드\",\"price\":30000,\"stock\":5,"
                                + "\"imageUrls\":[\"https://other/x.png\"]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("상품에 없는 이미지입니다. url=https://other/x.png"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
        verify(productService).delete(1L);
    }

    @Test
    void deleteMissingProductReturns404() throws Exception {
        doThrow(new ProductNotFoundException(99L)).when(productService).delete(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    private static MockMultipartFile productPart(String json) {
        return new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }
}
