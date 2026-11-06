package com.example.demo1.product.dto;

/**
 * @param imageUrl 0번 이미지의 경로. 이미지가 없으면 null
 */
public record ProductListResponse(
        Long id,
        String name,
        int price,
        int stock,
        String imageUrl) {
}
