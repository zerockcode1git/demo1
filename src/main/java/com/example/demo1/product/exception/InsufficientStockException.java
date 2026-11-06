package com.example.demo1.product.exception;

/**
 * 현재 재고보다 많은 수량을 차감하려고 할 때 발생한다.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(int stock, int quantity) {
        super("재고가 부족합니다. 현재 재고=" + stock + ", 요청 수량=" + quantity);
    }
}
