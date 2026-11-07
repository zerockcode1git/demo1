package com.example.demo1.order.entity;

/**
 * 주문 상태. DB에는 이름 그대로 문자열로 저장한다.
 * <pre>
 * PENDING ──complete()──▶ COMPLETED
 *    │                        │
 *    └──────cancel()──────────┴──▶ CANCELED
 * </pre>
 * 주문을 만들면 PENDING이고, 재고를 모두 차감하면 같은 트랜잭션 안에서 바로 COMPLETED가 된다.
 * 그래서 저장된 주문은 보통 COMPLETED나 CANCELED다.
 */
public enum OrderStatus {

    PENDING("주문중"),
    COMPLETED("주문 완료"),
    CANCELED("주문 취소");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
