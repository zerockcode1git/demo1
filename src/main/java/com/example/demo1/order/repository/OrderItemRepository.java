package com.example.demo1.order.repository;

import com.example.demo1.order.dto.OrderItemResponse;
import com.example.demo1.order.entity.OrderItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 상품 조회.
 * <p>
 * 조건 oi.order.id는 order_item.order_id 컬럼을 바로 비교한다. orders 테이블과 조인하지 않는다.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 여러 주문의 주문 상품을 상품의 0번 이미지와 함께 쿼리 한 번으로 조회한다.
     * 주문 목록 페이지의 주문 ID들을 넘긴다. 주문마다 따로 조회하면 페이지 크기만큼 쿼리가 더 나간다(N+1).
     * <ul>
     *     <li>OrderItem에는 상품 연관관계가 없어서 on p.id = oi.productId 조건으로 엔티티끼리 조인한다.</li>
     *     <li>0번 이미지는 상품마다 하나라 조인해도 주문 상품 행 수가 늘지 않는다.</li>
     *     <li>left join이라 상품이 삭제됐거나(@SQLRestriction) 이미지가 없어도 주문 상품은 나온다. 이때 imageUrl은 null이다.</li>
     * </ul>
     */
    @Query("""
            select new com.example.demo1.order.dto.OrderItemResponse(
                oi.order.id, oi.productId, oi.productName, oi.unitPrice, oi.quantity, i.imageUrl)
            from OrderItem oi
            left join Product p on p.id = oi.productId
            left join p.images i on i.imageIndex = 0
            where oi.order.id in :orderIds
            order by oi.order.id desc, oi.id asc
            """)
    List<OrderItemResponse> findResponsesByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

    /**
     * 주문 하나의 주문 상품 엔티티. 취소할 때 재고를 되돌리는 데 쓴다.
     */
    @Query("select oi from OrderItem oi where oi.order.id = :orderId order by oi.id asc")
    List<OrderItem> findAllByOrderId(@Param("orderId") Long orderId);
}
