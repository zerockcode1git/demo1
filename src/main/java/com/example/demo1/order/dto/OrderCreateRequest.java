package com.example.demo1.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param items 주문할 상품 목록. 1~50개. 같은 상품이 여러 번 있으면 수량을 합친다.
 */
public record OrderCreateRequest(
        @NotEmpty(message = "주문할 상품을 하나 이상 넣어 주세요.")
        @Size(max = 50, message = "한 번에 주문할 수 있는 상품은 50개까지입니다.")
        List<@NotNull(message = "주문 상품을 입력해 주세요.") @Valid OrderLineRequest> items) {
}
