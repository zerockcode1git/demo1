package com.example.demo1.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @param stock     처음 재고 수량. 0 이상
 * @param imageUrls 이미지 경로 목록. 목록 순서대로 이미지 번호가 매겨지며, 생략하면 이미지 없이 등록
 */
public record ProductCreateRequest(
        @NotBlank(message = "상품명을 입력해 주세요.") String name,
        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Integer price,
        @NotNull(message = "재고 수량을 입력해 주세요.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.") Integer stock,
        List<@NotBlank(message = "이미지 경로를 입력해 주세요.") String> imageUrls) {

    public ProductCreateRequest {
        imageUrls = imageUrls == null ? List.of() : imageUrls;
    }
}
