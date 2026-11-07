package com.example.demo1.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 주문할 상품 하나와 수량.
 *
 * @param productId 상품 ID
 * @param quantity  수량. 1~999
 */
public record OrderLineRequest(
        @NotNull(message = "상품 ID를 입력해 주세요.") Long productId,
        @NotNull(message = "수량을 입력해 주세요.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        @Max(value = 999, message = "수량은 999 이하여야 합니다.") Integer quantity) {
}
