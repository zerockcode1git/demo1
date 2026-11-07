package com.example.demo1.order.repository;

import com.example.demo1.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문은 주문 상품 목록을 갖지 않는다(주문 상품 → 주문 단방향). 그래서 주문을 조회해도 주문 상품은 읽지 않고,
 * 컬렉션을 fetch join하면서 페이징할 때 생기는 문제(전체를 메모리에 올려 페이징)도 생기지 않는다.
 * 주문 상품은 {@link OrderItemRepository}로 따로, 한 번에 조회한다.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 주문자의 주문 목록 한 페이지. 주문 쿼리 1번과 전체 개수 쿼리 1번이 실행된다.
     * 정렬은 Pageable로 받는다. (orderer_email_key, id) 인덱스를 타도록 id 정렬을 쓴다.
     */
    @Query(value = "select o from Order o where o.ordererEmailKey = :emailKey",
            countQuery = "select count(o) from Order o where o.ordererEmailKey = :emailKey")
    Page<Order> findPageByOrdererEmailKey(@Param("emailKey") String emailKey, Pageable pageable);

    /**
     * 취소할 주문을 비관적 쓰기 잠금으로 조회한다. 같은 주문을 동시에 두 번 취소해 재고가 두 번 복원되는 것을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
