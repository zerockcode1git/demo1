package com.example.demo1.product.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * @param page 1부터 시작하는 현재 페이지 번호
 */
public record ProductPageResponse(
        List<ProductListResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static ProductPageResponse from(Page<ProductListResponse> products) {
        return new ProductPageResponse(
                products.getContent(),
                // Spring Data의 0부터 시작하는 번호를 요청과 같은 1부터 시작하는 번호로 바꾼다.
                products.getNumber() + 1,
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.hasNext());
    }
}
