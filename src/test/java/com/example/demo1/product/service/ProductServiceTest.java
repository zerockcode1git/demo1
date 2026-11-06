package com.example.demo1.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo1.product.dto.ProductCreateRequest;
import com.example.demo1.product.dto.ProductImageResponse;
import com.example.demo1.product.dto.ProductListResponse;
import com.example.demo1.product.dto.ProductPageRequest;
import com.example.demo1.product.dto.ProductPageResponse;
import com.example.demo1.product.dto.ProductResponse;
import com.example.demo1.product.dto.ProductUpdateRequest;
import com.example.demo1.product.dto.ProductUpdateResult;
import com.example.demo1.product.exception.InvalidProductImageException;
import com.example.demo1.product.exception.ProductNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * application-local.yaml의 실제 DB에 접속해 실행한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 데이터가 남지 않는다.
 * 하나의 트랜잭션으로 실행되므로 동시 요청의 잠금 대기와 실제 롤백은 검증하지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import(ProductService.class)
class ProductServiceTest {

    private static final Long MISSING_ID = -1L;

    @Autowired
    private ProductService productService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createStartsWithZeroStockAndNumbersImages() {
        ProductResponse response = productService.create(
                new ProductCreateRequest("키보드", 30000, 0, List.of("front.jpg", "side.jpg")));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("키보드");
        assertThat(response.price()).isEqualTo(30000);
        assertThat(response.stock()).isZero();
        assertThat(response.images()).containsExactly(
                new ProductImageResponse("front.jpg", 0),
                new ProductImageResponse("side.jpg", 1));
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void createWithoutImages() {
        ProductResponse response = productService.create(new ProductCreateRequest("키보드", 30000, 0, null));
        flushAndClear();

        assertThat(response.images()).isEmpty();
        assertThat(productService.getOne(response.id()).images()).isEmpty();
    }

    @Test
    void getOneReturnsProductWithAllImages() {
        Long id = createAndClear("키보드", "front.jpg", "side.jpg");

        ProductResponse response = productService.getOne(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("키보드");
        assertThat(response.images()).containsExactly(
                new ProductImageResponse("front.jpg", 0),
                new ProductImageResponse("side.jpg", 1));
    }

    @Test
    void getAllReturnsTenNewestByDefaultWithMainImage() {
        List<Long> newestFirst = createProducts(11).reversed();

        ProductPageResponse response = productService.getAll(new ProductPageRequest(null, null, null));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isGreaterThanOrEqualTo(11);
        assertThat(response.totalPages()).isGreaterThanOrEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).extracting(ProductListResponse::id)
                .containsExactlyElementsOf(newestFirst.subList(0, 10));
        assertThat(response.content()).extracting(ProductListResponse::imageUrl)
                .containsOnly("front.jpg");
    }

    @Test
    void getAllReturnsRequestedPageAndSize() {
        List<Long> newestFirst = createProducts(6).reversed();

        ProductPageResponse response = productService.getAll(new ProductPageRequest(2, 3, null));

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.content()).extracting(ProductListResponse::id)
                .containsExactlyElementsOf(newestFirst.subList(3, 6));
    }

    @Test
    void updateChangesFieldsAndReplacesImages() {
        Long id = createAndClear("키보드", "front.jpg", "side.jpg");
        ProductResponse before = productService.getOne(id);
        entityManager.clear();

        ProductResponse response = productService.update(
                id, new ProductUpdateRequest("마우스", 10000, 0, List.of()), List.of("back.jpg")).product();
        flushAndClear();

        List<ProductImageResponse> expectedImages = List.of(new ProductImageResponse("back.jpg", 0));
        assertThat(response.name()).isEqualTo("마우스");
        assertThat(response.price()).isEqualTo(10000);
        assertThat(response.images()).isEqualTo(expectedImages);
        assertThat(response.updatedAt()).isAfter(before.updatedAt());

        ProductResponse found = productService.getOne(id);
        assertThat(found.name()).isEqualTo("마우스");
        assertThat(found.price()).isEqualTo(10000);
        assertThat(found.images()).isEqualTo(expectedImages);
    }

    @Test
    void updateWithEmptyImagesRemovesAllImages() {
        Long id = createAndClear("키보드", "front.jpg");

        productService.update(id, new ProductUpdateRequest("키보드", 30000, 0, List.of()), List.of());
        flushAndClear();

        assertThat(productService.getOne(id).images()).isEmpty();
    }

    @Test
    void updateChangesStock() {
        Long id = createAndClear("키보드", "front.jpg");

        ProductResponse response = productService.update(
                id, new ProductUpdateRequest("키보드", 30000, 7, List.of()), List.of()).product();
        flushAndClear();

        assertThat(response.stock()).isEqualTo(7);
        assertThat(productService.getOne(id).stock()).isEqualTo(7);
    }

    @Test
    void updateKeepsSelectedImagesInGivenOrderAndAppendsNewImages() {
        Long id = createAndClear("키보드", "a.jpg", "b.jpg", "c.jpg");

        ProductUpdateResult result = productService.update(
                id, new ProductUpdateRequest("키보드", 30000, 0, List.of("c.jpg", "a.jpg")), List.of("d.jpg"));
        flushAndClear();

        List<ProductImageResponse> expectedImages = List.of(
                new ProductImageResponse("c.jpg", 0),
                new ProductImageResponse("a.jpg", 1),
                new ProductImageResponse("d.jpg", 2));
        assertThat(result.product().images()).isEqualTo(expectedImages);
        assertThat(result.removedImageUrls()).containsExactly("b.jpg");
        assertThat(productService.getOne(id).images()).isEqualTo(expectedImages);
    }

    @Test
    void updateRejectsImageUrlNotInProduct() {
        Long id = createAndClear("키보드", "a.jpg");
        ProductUpdateRequest request = new ProductUpdateRequest("마우스", 10000, 0, List.of("other.jpg"));

        assertThatThrownBy(() -> productService.update(id, request, List.of()))
                .isInstanceOf(InvalidProductImageException.class)
                .hasMessageContaining("other.jpg");
        entityManager.clear();

        ProductResponse found = productService.getOne(id);
        assertThat(found.name()).isEqualTo("키보드");
        assertThat(found.images()).containsExactly(new ProductImageResponse("a.jpg", 0));
    }

    @Test
    void updateRejectsDuplicateImageUrls() {
        Long id = createAndClear("키보드", "a.jpg");
        ProductUpdateRequest request = new ProductUpdateRequest("키보드", 30000, 0, List.of("a.jpg", "a.jpg"));

        assertThatThrownBy(() -> productService.update(id, request, List.of()))
                .isInstanceOf(InvalidProductImageException.class);
    }

    @Test
    void getAllSearchesByNameIgnoringCase() {
        String token = uniqueToken();
        Long keyboardId = createAndClear(token + " Keyboard");
        createAndClear(token + " Mouse");

        ProductPageResponse response = productService.getAll(new ProductPageRequest(null, null, " KEYBOARD "));

        assertThat(response.content()).extracting(ProductListResponse::id).contains(keyboardId);
        assertThat(response.content()).extracting(ProductListResponse::name)
                .allSatisfy(name -> assertThat(name.toLowerCase()).contains("keyboard"));

        ProductPageResponse byToken = productService.getAll(new ProductPageRequest(null, null, token));
        assertThat(byToken.totalElements()).isEqualTo(2);
    }

    @Test
    void getAllTreatsLikeWildcardsInKeywordAsLiterals() {
        String token = uniqueToken();
        Long percentId = createAndClear(token + "50%off");
        createAndClear(token + "50xoff");
        Long underscoreId = createAndClear(token + "a_b");
        createAndClear(token + "axb");

        assertThat(productService.getAll(new ProductPageRequest(null, null, token + "50%")).content())
                .extracting(ProductListResponse::id).containsExactly(percentId);
        assertThat(productService.getAll(new ProductPageRequest(null, null, token + "a_b")).content())
                .extracting(ProductListResponse::id).containsExactly(underscoreId);
    }

    @Test
    void deleteOnlyMarksProductAsDeleted() {
        Long id = createAndClear("키보드", "front.jpg");

        productService.delete(id);
        flushAndClear();

        assertThatThrownBy(() -> productService.getOne(id))
                .isInstanceOf(ProductNotFoundException.class);
        // 행과 이미지는 남아 있고 deleted만 true가 된다. @SQLRestriction을 거치지 않도록 SQL로 직접 확인한다.
        Object deleted = entityManager.createNativeQuery("select deleted from product where id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(deleted).isEqualTo(true);
        Object imageCount = entityManager.createNativeQuery("select count(*) from product_image where product_id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(((Number) imageCount).longValue()).isEqualTo(1);
    }

    @Test
    void deletedProductIsExcludedFromListAndCannotBeUpdatedOrDeletedAgain() {
        String token = uniqueToken();
        Long keptId = createAndClear(token + " 남는 상품");
        Long deletedId = createAndClear(token + " 지울 상품");
        productService.delete(deletedId);
        flushAndClear();

        ProductPageResponse response = productService.getAll(new ProductPageRequest(null, null, token));

        assertThat(response.content()).extracting(ProductListResponse::id).containsExactly(keptId);
        assertThat(response.totalElements()).isEqualTo(1);
        ProductUpdateRequest request = new ProductUpdateRequest("마우스", 10000, 0, List.of());
        assertThatThrownBy(() -> productService.update(deletedId, request, List.of()))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.delete(deletedId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void missingProductThrowsNotFound() {
        ProductUpdateRequest request = new ProductUpdateRequest("마우스", 10000, 0, List.of());

        assertThatThrownBy(() -> productService.getOne(MISSING_ID))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.update(MISSING_ID, request, List.of()))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.delete(MISSING_ID))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private List<Long> createProducts(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> createAndClear("상품 " + i, "front.jpg", "side.jpg"))
                .toList();
    }

    private Long createAndClear(String name, String... imageUrls) {
        Long id = productService.create(new ProductCreateRequest(name, 30000, 0, List.of(imageUrls))).id();
        flushAndClear();
        return id;
    }

    /**
     * 실제 DB에 다른 상품이 있어도 검색 결과가 섞이지 않도록 상품명에 붙이는 고유 문자열
     */
    private String uniqueToken() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
