package com.example.demo1.order.dto;

/**
 * 재고가 부족한 상품.
 *
 * @param stock     지금 재고
 * @param requested 주문 수량
 */
public record StockShortageResponse(Long productId, String productName, int stock, int requested) {
}
