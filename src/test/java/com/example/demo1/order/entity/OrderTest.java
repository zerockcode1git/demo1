package com.example.demo1.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo1.order.exception.InvalidOrderException;
import com.example.demo1.order.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 엔티티의 규칙만 검증하므로 DB에 접속하지 않는다.
 */
class OrderTest {

    private static final String EMAIL_KEY = "user1@example.com";

    @Test
    void newOrderIsPendingWithNoItems() {
        Order order = new Order(EMAIL_KEY);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalPrice()).isZero();
        assertThat(order.getItemCount()).isZero();
        assertThat(order.getCanceledAt()).isNull();
    }

    @Test
    void addItemReturnsItemReferencingOrderAndUpdatesTotals() {
        Order order = new Order(EMAIL_KEY);

        OrderItem first = order.addItem(1L, "키보드", 30000, 2);
        order.addItem(2L, "마우스", 15000, 1);

        assertThat(first.getOrder()).isSameAs(order);
        assertThat(first.getProductId()).isEqualTo(1L);
        assertThat(first.getProductName()).isEqualTo("키보드");
        assertThat(first.getUnitPrice()).isEqualTo(30000);
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getLinePrice()).isEqualTo(60000);
        assertThat(order.getTotalPrice()).isEqualTo(75000);
        assertThat(order.getItemCount()).isEqualTo(2);
    }

    @Test
    void linePriceDoesNotOverflowInt() {
        Order order = new Order(EMAIL_KEY);

        OrderItem item = order.addItem(1L, "비싼 상품", Integer.MAX_VALUE, 999);

        assertThat(item.getLinePrice()).isEqualTo((long) Integer.MAX_VALUE * 999);
        assertThat(order.getTotalPrice()).isEqualTo((long) Integer.MAX_VALUE * 999);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void addItemWithNonPositiveQuantityThrowsAndKeepsTotals(int quantity) {
        Order order = new Order(EMAIL_KEY);

        assertThatThrownBy(() -> order.addItem(1L, "키보드", 30000, quantity))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(order.getTotalPrice()).isZero();
        assertThat(order.getItemCount()).isZero();
    }

    @Test
    void completeChangesStatus() {
        Order order = orderWithOneItem();

        order.complete();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void completeWithoutItemsThrows() {
        Order order = new Order(EMAIL_KEY);

        assertThatThrownBy(order::complete).isInstanceOf(InvalidOrderException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void completeTwiceThrows() {
        Order order = orderWithOneItem();
        order.complete();

        assertThatThrownBy(order::complete).isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void addItemAfterCompleteThrowsAndKeepsTotals() {
        Order order = orderWithOneItem();
        order.complete();

        assertThatThrownBy(() -> order.addItem(2L, "마우스", 15000, 1))
                .isInstanceOf(InvalidOrderStatusException.class);
        assertThat(order.getItemCount()).isEqualTo(1);
        assertThat(order.getTotalPrice()).isEqualTo(30000);
    }

    @Test
    void cancelPendingOrder() {
        Order order = orderWithOneItem();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
    }

    @Test
    void cancelCompletedOrder() {
        Order order = orderWithOneItem();
        order.complete();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCanceledAt()).isNotNull();
    }

    @Test
    void cancelTwiceThrowsAndKeepsCanceledAt() {
        Order order = orderWithOneItem();
        order.cancel();
        var canceledAt = order.getCanceledAt();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStatusException.class)
                .hasMessage("주문 취소 상태의 주문은 취소할 수 없습니다.");
        assertThat(order.getCanceledAt()).isEqualTo(canceledAt);
    }

    @Test
    void addItemAfterCancelThrows() {
        Order order = orderWithOneItem();
        order.cancel();

        assertThatThrownBy(() -> order.addItem(2L, "마우스", 15000, 1))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void isOrderedByComparesEmailKey() {
        Order order = new Order(EMAIL_KEY);

        assertThat(order.isOrderedBy(EMAIL_KEY)).isTrue();
        assertThat(order.isOrderedBy("other@example.com")).isFalse();
    }

    private static Order orderWithOneItem() {
        Order order = new Order(EMAIL_KEY);
        order.addItem(1L, "키보드", 30000, 1);
        return order;
    }
}
