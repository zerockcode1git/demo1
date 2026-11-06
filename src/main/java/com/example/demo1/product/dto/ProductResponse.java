package com.example.demo1.product.dto;

import com.example.demo1.product.entity.Product;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        int price,
        int stock,
        List<ProductImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getImages().stream().map(ProductImageResponse::from).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
