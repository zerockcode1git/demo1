package com.example.demo1.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @param stock     수정 후 재고 수량. 0 이상
 * @param imageUrls 남겨 둘 기존 이미지 URL 목록. 이 순서대로 이미지 번호가 매겨지고, 새로 올린 파일은 그 뒤에 붙는다.
 *                  목록에서 빠진 기존 이미지는 삭제되며, 빈 목록이면 기존 이미지를 모두 삭제한다.
 *                  상품에 없는 URL은 넣을 수 없다.
 */
public record ProductUpdateRequest(
        @NotBlank(message = "상품명을 입력해 주세요.") String name,
        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Integer price,
        @NotNull(message = "재고 수량을 입력해 주세요.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.") Integer stock,
        @NotNull(message = "이미지 목록을 입력해 주세요.")
        List<@NotBlank(message = "이미지 경로를 입력해 주세요.") String> imageUrls) {
}
