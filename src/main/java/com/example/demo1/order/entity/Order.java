package com.example.demo1.order.entity;

import com.example.demo1.order.exception.InvalidOrderException;
import com.example.demo1.order.exception.InvalidOrderStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 주문. 주문 상품 여러 개로 이루어진다.
 * <p>
 * 연관관계는 주문 상품 → 주문 단방향(@ManyToOne)이다. 주문은 주문 상품 목록을 갖지 않는다.
 * 주문 상품은 {@link #addItem}으로만 만들고, 서비스가 주문을 저장한 뒤 따로 저장한다.
 * <p>
 * 주문은 수정하지 않는다. 바꾸려면 취소하고 다시 주문한다.
 * 재고 차감과 복원은 상품 잠금이 필요해 서비스에서 하고, 이 엔티티는 상태 규칙과 금액 계산만 맡는다.
 * <p>
 * order는 SQL 예약어라 테이블 이름을 orders로 한다.
 */
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_orderer_email_key_id", columnList = "orderer_email_key, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 주문한 사용자의 users.email_key(소문자 이메일).
     * 사용자를 연관관계로 두면 주문이 있는 사용자를 삭제할 수 없어 키 값만 저장한다.
     */
    @Column(nullable = false)
    private String ordererEmailKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * 주문 상품 금액의 합. 상품을 추가할 때 함께 계산한다.
     */
    @Column(nullable = false)
    private long totalPrice;

    /**
     * 주문 상품 수. "상품 3개" 같은 목록 요약에 쓰고, 주문 상품이 있는지 검사할 때도 쓴다.
     */
    @Column(nullable = false)
    private int itemCount;

    @Column
    private LocalDateTime canceledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 주문중(PENDING) 상태로 만든다. 상품을 추가한 뒤 {@link #complete()}를 호출한다.
     */
    public Order(String ordererEmailKey) {
        this.ordererEmailKey = ordererEmailKey;
        this.status = OrderStatus.PENDING;
    }

    /**
     * 주문 상품을 만들고 총액과 상품 수에 더한다. 주문중일 때만 할 수 있다.
     * 주문은 주문 상품 목록을 갖지 않으므로, 돌려받은 주문 상품은 호출한 쪽이 저장해야 한다.
     *
     * @param unitPrice 지금 상품 가격. 주문 상품에 복사해 둔다.
     * @return 이 주문을 참조하는 새 주문 상품
     */
    public OrderItem addItem(Long productId, String productName, int unitPrice, int quantity) {
        requireStatus(OrderStatus.PENDING, "상품 추가");
        OrderItem item = new OrderItem(this, productId, productName, unitPrice, quantity);
        try {
            totalPrice = Math.addExact(totalPrice, item.getLinePrice());
        } catch (ArithmeticException e) {
            throw new InvalidOrderException("주문 금액이 너무 큽니다.");
        }
        itemCount++;
        return item;
    }

    /**
     * 재고 차감을 마친 주문을 완료한다.
     */
    public void complete() {
        requireStatus(OrderStatus.PENDING, "완료");
        if (itemCount == 0) {
            throw new InvalidOrderException("주문 상품이 없습니다.");
        }
        status = OrderStatus.COMPLETED;
    }

    /**
     * 주문을 취소한다. 주문중이나 주문 완료 상태에서 할 수 있다. 재고 복원은 서비스에서 한다.
     */
    public void cancel() {
        if (status == OrderStatus.CANCELED) {
            throw new InvalidOrderStatusException(status, "취소");
        }
        status = OrderStatus.CANCELED;
        canceledAt = LocalDateTime.now();
    }

    public boolean isOrderedBy(String emailKey) {
        return ordererEmailKey.equals(emailKey);
    }

    private void requireStatus(OrderStatus expected, String action) {
        if (status != expected) {
            throw new InvalidOrderStatusException(status, action);
        }
    }
}
