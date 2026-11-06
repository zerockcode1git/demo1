package com.example.demo1.product.exception;

/**
 * 재고나 증감 수량의 값이 올바르지 않을 때 발생한다.
 */
public class InvalidStockException extends RuntimeException {

    public InvalidStockException(String message) {
        super(message);
    }
}
