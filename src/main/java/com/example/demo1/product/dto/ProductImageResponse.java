package com.example.demo1.product.dto;

import com.example.demo1.product.entity.ProductImage;

public record ProductImageResponse(String imageUrl, int imageIndex) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.imageUrl(), image.imageIndex());
    }
}
