package com.example.demo1.order.exception;

/**
 * 주문 내용이 올바르지 않을 때 발생한다. 예: 주문 상품이 없음, 수량이 0 이하, 금액이 너무 큼
 */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
