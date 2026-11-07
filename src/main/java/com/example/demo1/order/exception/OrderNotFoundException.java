package com.example.demo1.order.exception;

/**
 * 주문이 없거나, 다른 사람의 주문일 때 발생한다.
 * 다른 사람의 주문을 403으로 거부하면 그 번호의 주문이 있다는 사실이 드러나므로 없는 주문과 똑같이 처리한다.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("주문을 찾을 수 없습니다. id=" + id);
    }
}
