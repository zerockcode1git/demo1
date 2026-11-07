package com.example.demo1.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 주문 상품. OrderItemRepository의 조회 결과를 바로 이 DTO로 받는다.
 *
 * @param orderId     주문 ID. 서비스가 주문별로 묶을 때 쓰고, 응답에는 넣지 않는다.
 * @param productId   상품 ID
 * @param productName 주문 당시 상품명
 * @param unitPrice   주문 당시 가격
 * @param quantity    수량
 * @param linePrice   가격 × 수량
 * @param imageUrl    상품의 지금 0번 이미지. 상품이 삭제됐거나 이미지가 없으면 null
 */
public record OrderItemResponse(
        @JsonIgnore Long orderId,
        Long productId,
        String productName,
        int unitPrice,
        int quantity,
        long linePrice,
        String imageUrl) {

    /**
     * JPQL의 select new에서 쓰는 생성자. 금액은 여기서 계산한다.
     */
    public OrderItemResponse(Long orderId, Long productId, String productName, int unitPrice, int quantity,
                             String imageUrl) {
        this(orderId, productId, productName, unitPrice, quantity, (long) unitPrice * quantity, imageUrl);
    }
}
