package com.example.demo1.product.exception;

/**
 * 수정 요청의 기존 이미지 목록에 상품에 없는 URL이나 중복된 URL이 있을 때 발생한다.
 */
public class InvalidProductImageException extends RuntimeException {

    public InvalidProductImageException(String message) {
        super(message);
    }
}
