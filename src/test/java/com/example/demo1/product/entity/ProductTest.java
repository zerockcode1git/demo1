package com.example.demo1.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo1.product.exception.InsufficientStockException;
import com.example.demo1.product.exception.InvalidStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 엔티티의 규칙만 검증하므로 DB에 접속하지 않는다.
 */
class ProductTest {

    @Test
    void newProductStartsWithZeroStock() {
        assertThat(new Product("키보드", 30000).getStock()).isZero();
    }

    @Test
    void changeStockToNegativeThrowsAndKeepsStock() {
        Product product = productWithStock(10);

        assertThatThrownBy(() -> product.changeStock(-1))
                .isInstanceOf(InvalidStockException.class);
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void increaseStock() {
        Product product = productWithStock(10);

        product.increaseStock(5);

        assertThat(product.getStock()).isEqualTo(15);
    }

    @Test
    void increaseStockBeyondMaximumThrowsAndKeepsStock() {
        Product product = productWithStock(Integer.MAX_VALUE);

        assertThatThrownBy(() -> product.increaseStock(1))
                .isInstanceOf(InvalidStockException.class);
        assertThat(product.getStock()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void decreaseStock() {
        Product product = productWithStock(10);

        product.decreaseStock(3);

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void decreaseStockToZero() {
        Product product = productWithStock(10);

        product.decreaseStock(10);

        assertThat(product.getStock()).isZero();
    }

    @Test
    void decreaseStockBeyondCurrentThrowsAndKeepsStock() {
        Product product = productWithStock(10);

        assertThatThrownBy(() -> product.decreaseStock(11))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("재고가 부족합니다. 현재 재고=10, 요청 수량=11");
        assertThat(product.getStock()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveQuantityThrowsAndKeepsStock(int quantity) {
        Product product = productWithStock(10);

        assertThatThrownBy(() -> product.increaseStock(quantity))
                .isInstanceOf(InvalidStockException.class);
        assertThatThrownBy(() -> product.decreaseStock(quantity))
                .isInstanceOf(InvalidStockException.class);
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void imagesGetIndexInAddedOrder() {
        Product product = new Product("키보드", 30000);

        product.addImage("front.jpg");
        product.addImage("side.jpg");

        assertThat(product.getImages()).containsExactly(
                new ProductImage("front.jpg", 0),
                new ProductImage("side.jpg", 1));
    }

    @Test
    void imageIndexRestartsFromZeroAfterClear() {
        Product product = new Product("키보드", 30000);
        product.addImage("front.jpg");
        product.addImage("side.jpg");

        product.clearImages();
        product.addImage("back.jpg");

        assertThat(product.getImages()).containsExactly(new ProductImage("back.jpg", 0));
    }

    @Test
    void imagesCannotBeModifiedDirectly() {
        Product product = new Product("키보드", 30000);

        assertThatThrownBy(() -> product.getImages().add(new ProductImage("front.jpg", 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Product productWithStock(int stock) {
        Product product = new Product("키보드", 30000);
        product.changeStock(stock);
        return product;
    }
}
