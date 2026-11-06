package com.example.demo1.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * @param imageUrl   이미지 경로
 * @param imageIndex 상품 안에서의 이미지 번호. 0부터 시작
 */
@Embeddable
public record ProductImage(
        @Column(name = "image_url", nullable = false) String imageUrl,
        @Column(name = "image_index", nullable = false) int imageIndex) {
}
