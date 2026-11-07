package com.example.demo1.order.dto;

import com.example.demo1.order.entity.Order;
import com.example.demo1.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param status            주문 상태. PENDING, COMPLETED, CANCELED
 * @param statusDescription 상태 설명. 주문중, 주문 완료, 주문 취소
 * @param totalPrice        주문 상품 금액의 합
 * @param itemCount         주문 상품 수
 * @param canceledAt        취소 시각. 취소하지 않았으면 null
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        String statusDescription,
        long totalPrice,
        int itemCount,
        LocalDateTime createdAt,
        LocalDateTime canceledAt,
        List<OrderItemResponse> items) {

    public static OrderResponse of(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getStatus().getDescription(),
                order.getTotalPrice(),
                order.getItemCount(),
                order.getCreatedAt(),
                order.getCanceledAt(),
                List.copyOf(items));
    }
}
