package com.example.demo1.order.entity;

import com.example.demo1.order.exception.InvalidOrderException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품. 상품 하나와 수량의 묶음이다.
 * <p>
 * 상품을 연관관계(@ManyToOne Product)로 두지 않고 상품 ID, 상품명, 주문 당시 가격을 복사해 둔다.
 * <ul>
 *     <li>주문한 뒤 상품 가격이나 이름이 바뀌어도 주문 내역은 주문할 때 그대로여야 한다.</li>
 *     <li>상품은 삭제 표시만 하고 @SQLRestriction으로 조회에서 뺀다. 연관관계로 두면 삭제된 상품이 든 주문을
 *         읽을 때 상품을 찾지 못해 오류가 난다.</li>
 * </ul>
 * 주문 상품은 주문을 통해서만 만든다({@link Order#addItem}).
 * <p>
 * 연관관계는 주문 상품 → 주문 단방향(@ManyToOne)이다. 외래 키 order_item.order_id가 있는 쪽이 관계를 관리한다.
 */
@Entity
@Table(name = "order_item", indexes = @Index(name = "idx_order_item_order_id", columnList = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 주문 상품을 조회할 때 주문까지 읽지 않도록 지연 로딩한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    /**
     * 주문 당시 상품 가격
     */
    @Column(nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int quantity;

    OrderItem(Order order, Long productId, String productName, int unitPrice, int quantity) {
        if (quantity <= 0) {
            throw new InvalidOrderException("주문 수량은 1 이상이어야 합니다. 상품 ID=" + productId + ", 수량=" + quantity);
        }
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    /**
     * 가격 × 수량. int 범위를 넘을 수 있어 long으로 계산한다.
     */
    public long getLinePrice() {
        return (long) unitPrice * quantity;
    }
}
