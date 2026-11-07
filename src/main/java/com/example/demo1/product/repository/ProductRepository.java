package com.example.demo1.product.repository;

import com.example.demo1.product.dto.ProductListResponse;
import com.example.demo1.product.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 상품과 이미지를 한 번의 쿼리로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "images")
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findWithImagesById(@Param("id") Long id);

    /**
     * 상품 행을 비관적 쓰기 잠금으로 조회한다. 트랜잭션이 끝날 때까지 다른 잠금 요청은 대기한다.
     * 외부 조인과 함께 잠그지 않도록 이미지는 함께 조회하지 않고 지연 로딩한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * 여러 상품을 id 순서로 비관적 쓰기 잠금한다. 주문할 때와 주문을 취소할 때 쓴다.
     * <p>
     * 두 트랜잭션이 같은 상품들을 서로 다른 순서로 잠그면 서로를 기다리는 교착 상태(deadlock)가 생긴다.
     * 항상 id 순서로 잠가서 이를 막는다. 삭제된 상품은 @SQLRestriction 때문에 결과에서 빠진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * 상품명이 패턴과 일치하는 상품 목록을 0번 이미지 하나와 함께 조회한다. 이미지가 없는 상품도 포함된다.
     *
     * @param namePattern 소문자로 된 LIKE 패턴. 이스케이프 문자는 역슬래시(\)
     */
    @Query(value = """
            select new com.example.demo1.product.dto.ProductListResponse(
                p.id, p.name, p.price, p.stock, i.imageUrl)
            from Product p
            left join p.images i on i.imageIndex = 0
            where lower(p.name) like :namePattern escape '\\'
            """,
            countQuery = "select count(p) from Product p where lower(p.name) like :namePattern escape '\\'")
    Page<ProductListResponse> findAllWithMainImage(@Param("namePattern") String namePattern, Pageable pageable);
}
