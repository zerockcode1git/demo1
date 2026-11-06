package com.example.demo1.product.entity;

import com.example.demo1.product.exception.InsufficientStockException;
import com.example.demo1.product.exception.InvalidStockException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 삭제는 deleted 표시만 한다. @SQLRestriction으로 삭제된 상품은 모든 조회에서 빠진다.
 */
@Entity
@Table(name = "product")
@SQLRestriction("deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    /**
     * 기존 행이 있는 테이블에 컬럼을 추가할 수 있도록 DB 기본값을 false로 둔다.
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 이미지는 값 객체라 상품과 함께 저장, 삭제되고 목록에서 빠지면 DB에서도 삭제된다.
     * 조회할 때는 이미지 번호 순서로 정렬된다.
     */
    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(
            name = "product_image",
            joinColumns = @JoinColumn(name = "product_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_product_image_product_id_image_index",
                    columnNames = {"product_id", "image_index"}))
    @OrderBy("imageIndex ASC")
    private List<ProductImage> images = new ArrayList<>();

    /**
     * 재고 0으로 시작한다.
     */
    public Product(String name, int price) {
        this(name, price, 0);
    }

    public Product(String name, int price, int stock) {
        validateStock(stock);
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePrice(int price) {
        this.price = price;
    }

    /**
     * 행을 지우지 않고 삭제 표시만 한다. 이미지도 그대로 남는다.
     */
    public void delete() {
        this.deleted = true;
    }

    public void changeStock(int stock) {
        validateStock(stock);
        this.stock = stock;
    }

    public void increaseStock(int quantity) {
        validateQuantity(quantity);
        if (quantity > Integer.MAX_VALUE - stock) {
            throw new InvalidStockException("재고가 최대값을 초과합니다. 현재 재고=" + stock + ", 요청 수량=" + quantity);
        }
        stock += quantity;
    }

    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        if (quantity > stock) {
            throw new InsufficientStockException(stock, quantity);
        }
        stock -= quantity;
    }

    private static void validateStock(int stock) {
        if (stock < 0) {
            throw new InvalidStockException("재고는 0 이상이어야 합니다. 입력값=" + stock);
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidStockException("증감 수량은 1 이상이어야 합니다. 입력값=" + quantity);
        }
    }

    /**
     * 목록을 직접 수정하지 못하게 해서 이미지 변경이 아래 메서드만 거치도록 한다.
     */
    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public void addImage(String imageUrl) {
        images.add(new ProductImage(imageUrl, images.size()));
    }

    public void clearImages() {
        images.clear();
    }
}
