package com.example.demo1.order.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * @param page 1부터 시작하는 현재 페이지 번호
 */
public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static OrderPageResponse from(Page<OrderResponse> orders) {
        return new OrderPageResponse(
                orders.getContent(),
                // Spring Data의 0부터 시작하는 번호를 요청과 같은 1부터 시작하는 번호로 바꾼다.
                orders.getNumber() + 1,
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.hasNext());
    }
}
