package com.example.demo1.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo1.product.dto.ProductListResponse;
import com.example.demo1.product.entity.Product;
import com.example.demo1.product.entity.ProductImage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * application-local.yaml의 실제 DB에 접속해 실행한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 데이터가 남지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void create() {
        Product product = new Product("키보드", 30000);
        product.addImage("front.jpg");

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("키보드");
        assertThat(saved.getPrice()).isEqualTo(30000);
        assertThat(saved.getStock()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void update() {
        Long id = saveAndClear("키보드", "front.jpg");
        Product before = productRepository.findById(id).orElseThrow();
        var createdAt = before.getCreatedAt();
        var updatedAt = before.getUpdatedAt();

        before.changeName("마우스");
        before.changePrice(10000);
        before.changeStock(5);
        productRepository.flush();
        entityManager.clear();

        Product after = productRepository.findById(id).orElseThrow();
        assertThat(after.getName()).isEqualTo("마우스");
        assertThat(after.getPrice()).isEqualTo(10000);
        assertThat(after.getStock()).isEqualTo(5);
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void replaceImages() {
        Long id = saveAndClear("키보드", "front.jpg", "side.jpg");
        Product product = productRepository.findById(id).orElseThrow();

        product.clearImages();
        product.addImage("back.jpg");
        product.addImage("top.jpg");
        productRepository.flush();
        entityManager.clear();

        assertThat(productRepository.findWithImagesById(id).orElseThrow().getImages()).containsExactly(
                new ProductImage("back.jpg", 0),
                new ProductImage("top.jpg", 1));
    }

    @Test
    void deleteRemovesImages() {
        Long id = saveAndClear("키보드", "front.jpg", "side.jpg");

        productRepository.deleteById(id);
        productRepository.flush();
        entityManager.clear();

        assertThat(productRepository.findById(id)).isEmpty();
        assertThat(countImages(id)).isZero();
    }

    @Test
    void findByIdLoadsImagesLazily() {
        Long id = saveAndClear("키보드", "front.jpg");

        Product found = productRepository.findById(id).orElseThrow();

        assertThat(isImagesLoaded(found)).isFalse();
    }

    @Test
    void findWithImagesByIdLoadsImagesInIndexOrder() {
        Long id = saveAndClear("키보드", "front.jpg", "side.jpg", "back.jpg");

        Product found = productRepository.findWithImagesById(id).orElseThrow();

        assertThat(isImagesLoaded(found)).isTrue();
        assertThat(found.getImages()).containsExactly(
                new ProductImage("front.jpg", 0),
                new ProductImage("side.jpg", 1),
                new ProductImage("back.jpg", 2));
    }

    @Test
    void findWithImagesByIdReturnsProductWithoutImages() {
        Long id = saveAndClear("키보드");

        Product found = productRepository.findWithImagesById(id).orElseThrow();

        assertThat(found.getImages()).isEmpty();
    }

    @Test
    void findWithImagesByIdReturnsEmptyWhenMissing() {
        assertThat(productRepository.findWithImagesById(-1L)).isEmpty();
    }

    @Test
    void findByIdForUpdateReturnsProductWithImages() {
        Long id = saveAndClear("키보드", "front.jpg");

        Product found = productRepository.findByIdForUpdate(id).orElseThrow();

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getImages()).containsExactly(new ProductImage("front.jpg", 0));
    }

    @Test
    void findByIdForUpdateReturnsEmptyWhenMissing() {
        assertThat(productRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void findAllWithMainImageReturnsOnlyFirstImagePerProduct() {
        Long withImagesId = saveAndClear("키보드", "front.jpg", "side.jpg");
        Long withoutImagesId = saveAndClear("마우스");

        Page<ProductListResponse> page = productRepository.findAllWithMainImage("%",
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(page.getContent()).containsExactly(
                new ProductListResponse(withoutImagesId, "마우스", 10000, 0, null),
                new ProductListResponse(withImagesId, "키보드", 10000, 0, "front.jpg"));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    private Long saveAndClear(String name, String... imageUrls) {
        Product product = new Product(name, 10000);
        for (String imageUrl : imageUrls) {
            product.addImage(imageUrl);
        }
        Long id = productRepository.saveAndFlush(product).getId();
        entityManager.clear();
        return id;
    }

    private boolean isImagesLoaded(Product product) {
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(product, "images");
    }

    private long countImages(Long productId) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from product_image where product_id = :id")
                .setParameter("id", productId)
                .getSingleResult()).longValue();
    }
}
