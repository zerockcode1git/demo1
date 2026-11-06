package com.example.demo1.product.service;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.dto.ProductUpdateResult;
import com.example.demo1.storage.dto.FileUploadResponse;
import com.example.demo1.storage.exception.InvalidFileException;
import com.example.demo1.storage.service.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 파일이 함께 오는 상품 등록과 수정을 처리한다.
 * <p>
 * 업로드와 삭제는 외부 네트워크 호출이라 DB 트랜잭션 밖에서 한다.
 * <ul>
 *     <li>업로드 → {@link ProductService} 호출(트랜잭션) 순서로 처리한다.</li>
 *     <li>업로드 도중이나 DB 저장 단계에서 실패하면 이번에 올린 파일을 지운다.</li>
 *     <li>수정으로 빠진 기존 이미지는 DB 커밋이 끝난 뒤 저장소에서 지운다.</li>
 * </ul>
 * 이미지 파일(Content-Type이 image/*)만 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCommandService {

    private static final MediaType IMAGE = MediaType.parseMediaType("image/*");

    private final ProductService productService;
    private final FileStorageService fileStorageService;

    /**
     * @param request 상품 정보. imageUrls는 무시하고 업로드한 파일의 URL로 채운다.
     * @param files   이미지 파일 목록. 목록 순서대로 이미지 번호가 매겨지며, 없으면 이미지 없이 등록
     */
    public ProductResponse register(ProductCreateRequest request, List<MultipartFile> files) {
        return withUploadedImages(files, imageUrls -> productService.create(
                new ProductCreateRequest(request.name(), request.price(), request.stock(), imageUrls)));
    }

    /**
     * @param request 상품 정보. imageUrls는 남겨 둘 기존 이미지 목록이다.
     * @param files   새로 추가할 이미지 파일. 남겨 둔 기존 이미지 뒤에 순서대로 붙는다.
     */
    public ProductResponse update(Long id, ProductUpdateRequest request, List<MultipartFile> files) {
        ProductUpdateResult result = withUploadedImages(files,
                newImageUrls -> productService.update(id, request, newImageUrls));
        // 여기까지 왔으면 DB 커밋이 끝났으므로 빠진 이미지 파일을 지운다.
        result.removedImageUrls().forEach(this::deleteQuietly);
        return result.product();
    }

    /**
     * 파일을 모두 검사하고 올린 뒤, 올린 URL 목록으로 action을 실행한다. 실패하면 올린 파일을 지운다.
     */
    private <T> T withUploadedImages(List<MultipartFile> files, Function<List<String>, T> action) {
        List<MultipartFile> images = files == null ? List.of() : files;
        // 하나라도 이미지가 아니면 아무것도 올리지 않도록 업로드 전에 모두 검사한다.
        images.forEach(this::validateImage);

        List<FileUploadResponse> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                uploaded.add(fileStorageService.upload(file));
            }
            return action.apply(uploaded.stream().map(FileUploadResponse::url).toList());
        } catch (RuntimeException e) {
            uploaded.forEach(file -> deleteQuietly(file.url()));
            throw e;
        }
    }

    /**
     * Content-Type이 image/*인지 확인한다. 클라이언트가 보낸 값을 기준으로 하며 파일 내용은 검사하지 않는다.
     */
    private void validateImage(MultipartFile file) {
        if (!isImage(file.getContentType())) {
            throw new InvalidFileException("이미지 파일만 업로드할 수 있습니다. 파일=" + file.getOriginalFilename());
        }
    }

    private boolean isImage(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return IMAGE.includes(type) && !type.isWildcardSubtype();
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    /**
     * 파일 정리 실패는 요청 결과를 바꾸지 않도록 로그만 남긴다.
     */
    private void deleteQuietly(String url) {
        try {
            fileStorageService.deleteByUrl(url);
        } catch (RuntimeException e) {
            log.warn("이미지 파일을 지우지 못했습니다. url={}", url, e);
        }
    }
}
