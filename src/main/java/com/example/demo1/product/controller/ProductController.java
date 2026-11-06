package com.example.demo1.product.controller;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductPageRequest;
import com.example.demo1.product.dto.ProductPageResponse;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.service.ProductCommandService;
import com.example.demo1.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 등록과 수정은 multipart/form-data로 받는다.
 * <ul>
 *     <li>product: 상품 정보 JSON. 이 파트의 Content-Type은 application/json이어야 한다.</li>
 *     <li>files: 이미지 파일. 여러 개 보낼 수 있고 생략할 수 있다.</li>
 * </ul>
 * Swagger UI는 기본적으로 product 파트를 Content-Type 없이 보내 415 오류가 나므로,
 * encoding으로 product 파트를 application/json으로 보내도록 지정한다.
 */
@Tag(name = "상품", description = "상품 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCommandService productCommandService;
    private final ProductService productService;

    @Operation(
            summary = "상품 등록",
            description = "상품 정보(product, JSON)와 이미지 파일(files)을 함께 보낸다. "
                    + "파일 순서대로 이미지 번호가 매겨지며, 파일을 생략하면 이미지 없이 등록한다. "
                    + "product의 imageUrls는 무시한다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "product", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestPart("product") ProductCreateRequest request,
                                  @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return productCommandService.register(request, files);
    }

    @Operation(summary = "상품 조회", description = "상품 하나를 이미지 전체와 함께 조회한다. 이미지는 이미지 번호 순서로 정렬된다.")
    @GetMapping("/{id}")
    public ProductResponse getOne(@PathVariable Long id) {
        return productService.getOne(id);
    }

    @Operation(summary = "상품 목록 조회", description = "최근 등록순으로 조회한다. 각 상품은 0번 이미지만 포함한다. "
            + "keyword를 주면 상품명에 검색어가 포함된 상품만 조회한다(대소문자 구분 없음).")
    @GetMapping
    public ProductPageResponse getAll(@ParameterObject @Valid @ModelAttribute ProductPageRequest request) {
        return productService.getAll(request);
    }

    @Operation(
            summary = "상품 수정",
            description = "이름, 가격, 재고와 이미지를 수정한다. "
                    + "product의 imageUrls에는 남겨 둘 기존 이미지 URL을 순서대로 넣고, 새 이미지는 files로 보낸다. "
                    + "새 이미지는 기존 이미지 뒤에 붙으며, imageUrls에서 빠진 기존 이미지는 삭제된다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "product", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestPart("product") ProductUpdateRequest request,
                                  @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return productCommandService.update(id, request, files);
    }

    @Operation(summary = "상품 삭제", description = "삭제 표시만 하며 데이터와 이미지 파일은 남는다. 삭제된 상품은 조회되지 않는다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
