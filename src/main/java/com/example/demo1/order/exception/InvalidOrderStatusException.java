package com.example.demo1.order.exception;

import com.example.demo1.order.entity.OrderStatus;

/**
 * 현재 상태에서 할 수 없는 작업을 요청했을 때 발생한다. 예: 이미 취소된 주문을 다시 취소
 */
public class InvalidOrderStatusException extends RuntimeException {

    public InvalidOrderStatusException(OrderStatus status, String action) {
        super(status.getDescription() + " 상태의 주문은 " + action + "할 수 없습니다.");
    }
}
