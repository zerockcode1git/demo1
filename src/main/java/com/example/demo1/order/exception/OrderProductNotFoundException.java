package com.example.demo1.order.exception;

import java.util.Collection;

/**
 * 주문하려는 상품 중 없거나 삭제된 상품이 있을 때 발생한다.
 */
public class OrderProductNotFoundException extends RuntimeException {

    public OrderProductNotFoundException(Collection<Long> productIds) {
        super("주문할 수 없는 상품입니다. 상품 ID=" + productIds);
    }
}
