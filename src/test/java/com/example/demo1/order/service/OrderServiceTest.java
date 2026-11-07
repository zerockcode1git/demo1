package com.example.demo1.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.demo1.order.dto.OrderCreateRequest;
import com.example.demo1.order.dto.OrderItemResponse;
import com.example.demo1.order.dto.OrderLineRequest;
import com.example.demo1.order.dto.OrderPageRequest;
import com.example.demo1.order.dto.OrderPageResponse;
import com.example.demo1.order.dto.OrderResponse;
import com.example.demo1.order.dto.StockShortageResponse;
import com.example.demo1.order.entity.OrderStatus;
import com.example.demo1.order.exception.InvalidOrderStatusException;
import com.example.demo1.order.exception.OrderNotFoundException;
import com.example.demo1.order.exception.OrderProductNotFoundException;
import com.example.demo1.order.exception.OutOfStockException;
import com.example.demo1.product.entity.Product;
import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * application-local.yaml의 실제 DB에 접속해 실행한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 데이터가 남지 않는다.
 * <p>
 * 하나의 트랜잭션으로 실행되므로 동시 주문의 잠금 대기와 교착 상태 방지는 검증하지 않는다.
 * <p>
 * 실행된 SQL 수는 Hibernate 통계(generate_statistics)로 센다.
 * 주문자 이메일은 테스트마다 새로 만들어, DB에 이미 있는 주문과 섞이지 않게 한다.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import(OrderService.class)
class OrderServiceTest {

    private static final Long MISSING_ID = -1L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private TokenPayload user;
    private TokenPayload otherUser;
    private TokenPayload admin;

    @BeforeEach
    void setUp() {
        user = requester(UserRole.ROLE_USER);
        otherUser = requester(UserRole.ROLE_USER);
        admin = requester(UserRole.ROLE_ADMIN);
    }

    // ---------------------------------------------------------------- 주문

    @Test
    void createDecreasesStockAndCompletesOrder() {
        Long keyboard = product("키보드", 30000, 10, "keyboard.png");
        Long mouse = product("마우스", 15000, 5);

        OrderResponse response = orderService.create(user, request(line(keyboard, 2), line(mouse, 1)));
        flushAndClear();

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.statusDescription()).isEqualTo("주문 완료");
        assertThat(response.totalPrice()).isEqualTo(75000);
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.canceledAt()).isNull();
        assertThat(response.items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::productName,
                        OrderItemResponse::unitPrice, OrderItemResponse::quantity, OrderItemResponse::linePrice,
                        OrderItemResponse::imageUrl)
                .containsExactly(
                        tuple(keyboard, "키보드", 30000, 2, 60000L, "keyboard.png"),
                        tuple(mouse, "마우스", 15000, 1, 15000L, null));
        assertThat(stockOf(keyboard)).isEqualTo(8);
        assertThat(stockOf(mouse)).isEqualTo(4);
    }

    @Test
    void createMergesSameProductInRequestOrder() {
        Long keyboard = product("키보드", 30000, 10);
        Long mouse = product("마우스", 15000, 5);

        OrderResponse response = orderService.create(user,
                request(line(keyboard, 2), line(mouse, 1), line(keyboard, 1)));
        flushAndClear();

        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::quantity)
                .containsExactly(tuple(keyboard, 3), tuple(mouse, 1));
        assertThat(stockOf(keyboard)).isEqualTo(7);
    }

    @Test
    void createCanUseAllStock() {
        Long keyboard = product("키보드", 30000, 3);

        orderService.create(user, request(line(keyboard, 3)));
        flushAndClear();

        assertThat(stockOf(keyboard)).isZero();
    }

    @Test
    void createKeepsPriceAtOrderTime() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 1))).id();
        entityManager.find(Product.class, keyboard).changePrice(99000);
        flushAndClear();

        OrderResponse response = orderService.getOne(user, orderId);

        assertThat(response.totalPrice()).isEqualTo(30000);
        assertThat(response.items()).extracting(OrderItemResponse::unitPrice).containsExactly(30000);
    }

    @Test
    void createWithAnyShortageReportsAllAndDecreasesNothing() {
        Long keyboard = product("키보드", 30000, 10);
        Long mouse = product("마우스", 15000, 1);
        Long monitor = product("모니터", 200000, 0);

        assertThatThrownBy(() -> orderService.create(user,
                request(line(keyboard, 2), line(mouse, 5), line(monitor, 1))))
                .isInstanceOfSatisfying(OutOfStockException.class, e -> assertThat(e.getShortages())
                        .containsExactlyInAnyOrder(
                                new StockShortageResponse(mouse, "마우스", 1, 5),
                                new StockShortageResponse(monitor, "모니터", 0, 1)));
        flushAndClear();

        assertThat(stockOf(keyboard)).isEqualTo(10);
        assertThat(stockOf(mouse)).isEqualTo(1);
        assertThat(countOrdersOf(user)).isZero();
    }

    @Test
    void createChecksMergedQuantityAgainstStock() {
        Long keyboard = product("키보드", 30000, 3);

        assertThatThrownBy(() -> orderService.create(user, request(line(keyboard, 2), line(keyboard, 2))))
                .isInstanceOfSatisfying(OutOfStockException.class, e -> assertThat(e.getShortages())
                        .containsExactly(new StockShortageResponse(keyboard, "키보드", 3, 4)));
    }

    @Test
    void createWithMissingProductThrows() {
        Long keyboard = product("키보드", 30000, 10);

        assertThatThrownBy(() -> orderService.create(user, request(line(keyboard, 1), line(MISSING_ID, 1))))
                .isInstanceOf(OrderProductNotFoundException.class)
                .hasMessageContaining(String.valueOf(MISSING_ID));
        flushAndClear();

        assertThat(stockOf(keyboard)).isEqualTo(10);
    }

    @Test
    void createWithDeletedProductThrows() {
        Long keyboard = product("키보드", 30000, 10);
        entityManager.find(Product.class, keyboard).delete();
        flushAndClear();

        assertThatThrownBy(() -> orderService.create(user, request(line(keyboard, 1))))
                .isInstanceOf(OrderProductNotFoundException.class);
    }

    // ---------------------------------------------------------------- 목록

    @Test
    void getMineReturnsOnlyMyOrdersNewestFirstWithItems() {
        Long keyboard = product("키보드", 30000, 100, "keyboard.png");
        Long mouse = product("마우스", 15000, 100);
        Long first = orderService.create(user, request(line(keyboard, 1))).id();
        Long second = orderService.create(user, request(line(keyboard, 2), line(mouse, 3))).id();
        orderService.create(otherUser, request(line(mouse, 1)));
        flushAndClear();

        OrderPageResponse page = orderService.getMine(user, new OrderPageRequest(1, 10));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(OrderResponse::id).containsExactly(second, first);
        assertThat(page.content().get(0).items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::quantity, OrderItemResponse::imageUrl)
                .containsExactly(tuple(keyboard, 2, "keyboard.png"), tuple(mouse, 3, null));
        assertThat(page.content().get(1).items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::quantity)
                .containsExactly(tuple(keyboard, 1));
    }

    @Test
    void getMinePagesByOrderNotByItem() {
        Long keyboard = product("키보드", 30000, 100);
        Long mouse = product("마우스", 15000, 100);
        Long monitor = product("모니터", 200000, 100);
        for (int i = 0; i < 3; i++) {
            orderService.create(user, request(line(keyboard, 1), line(mouse, 1), line(monitor, 1)));
        }
        flushAndClear();

        OrderPageResponse first = orderService.getMine(user, new OrderPageRequest(1, 2));
        OrderPageResponse second = orderService.getMine(user, new OrderPageRequest(2, 2));

        // 주문 상품 행이 아니라 주문 단위로 나뉘어, 각 주문의 상품 3개가 한 페이지에 모두 붙는다.
        assertThat(first.content()).hasSize(2).allSatisfy(order -> assertThat(order.items()).hasSize(3));
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).hasSize(1).allSatisfy(order -> assertThat(order.items()).hasSize(3));
        assertThat(second.hasNext()).isFalse();
    }

    /**
     * 주문 한 페이지, 전체 개수, 그 페이지 주문들의 주문 상품으로 쿼리는 3번이다.
     * 주문마다 주문 상품을 조회하면(N+1) 페이지 크기에 따라 쿼리 수가 늘어난다.
     */
    @Test
    void getMineRunsThreeQueriesRegardlessOfPageSize() {
        Long keyboard = product("키보드", 30000, 100, "keyboard.png");
        Long mouse = product("마우스", 15000, 100);
        for (int i = 0; i < 6; i++) {
            orderService.create(user, request(line(keyboard, 1), line(mouse, 1)));
        }
        flushAndClear();

        assertThat(countQueries(() -> orderService.getMine(user, new OrderPageRequest(1, 2)))).isEqualTo(3);
        assertThat(countQueries(() -> orderService.getMine(user, new OrderPageRequest(1, 5)))).isEqualTo(3);
    }

    @Test
    void getMineWithNoOrdersSkipsItemQuery() {
        OrderPageResponse[] page = new OrderPageResponse[1];

        long queries = countQueries(() -> page[0] = orderService.getMine(user, new OrderPageRequest(1, 10)));

        assertThat(page[0].content()).isEmpty();
        assertThat(page[0].totalElements()).isZero();
        // 첫 페이지가 페이지 크기보다 적게 나오면 Spring Data가 count를 생략하고, 주문이 없으면 주문 상품도 조회하지 않는다.
        assertThat(queries).isEqualTo(1);
    }

    @Test
    void listShowsDeletedProductWithoutImage() {
        Long keyboard = product("키보드", 30000, 10, "keyboard.png");
        orderService.create(user, request(line(keyboard, 1)));
        entityManager.find(Product.class, keyboard).delete();
        flushAndClear();

        OrderPageResponse page = orderService.getMine(user, new OrderPageRequest(1, 10));

        assertThat(page.content().get(0).items())
                .extracting(OrderItemResponse::productName, OrderItemResponse::imageUrl)
                .containsExactly(tuple("키보드", null));
    }

    @Test
    void getAllIncludesEveryonesOrders() {
        Long keyboard = product("키보드", 30000, 10);
        Long mine = orderService.create(user, request(line(keyboard, 1))).id();
        Long others = orderService.create(otherUser, request(line(keyboard, 1))).id();
        flushAndClear();

        OrderPageResponse page = orderService.getAll(new OrderPageRequest(1, 10));

        // DB에 다른 주문이 있을 수 있어 방금 만든 두 주문이 맨 앞(최근순)에 있는지만 본다.
        assertThat(page.content()).extracting(OrderResponse::id).startsWith(others, mine);
    }

    // ---------------------------------------------------------------- 상세

    @Test
    void getOneByOwner() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 2))).id();
        flushAndClear();

        OrderResponse response = orderService.getOne(user, orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.items()).extracting(OrderItemResponse::quantity).containsExactly(2);
    }

    @Test
    void getOneByAdmin() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 1))).id();
        flushAndClear();

        assertThat(orderService.getOne(admin, orderId).id()).isEqualTo(orderId);
    }

    @Test
    void getOneOfOtherUserThrowsNotFound() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 1))).id();
        flushAndClear();

        assertThatThrownBy(() -> orderService.getOne(otherUser, orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("주문을 찾을 수 없습니다. id=" + orderId);
    }

    @Test
    void getOneMissingThrowsNotFound() {
        assertThatThrownBy(() -> orderService.getOne(user, MISSING_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void emailCaseDoesNotMatter() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 1))).id();
        flushAndClear();
        TokenPayload upperCase = new TokenPayload(user.email().toUpperCase(), "대문자", user.roles());

        assertThat(orderService.getOne(upperCase, orderId).id()).isEqualTo(orderId);
    }

    // ---------------------------------------------------------------- 취소

    @Test
    void cancelRestoresStock() {
        Long keyboard = product("키보드", 30000, 10);
        Long mouse = product("마우스", 15000, 5);
        Long orderId = orderService.create(user, request(line(keyboard, 3), line(mouse, 2))).id();
        flushAndClear();

        OrderResponse response = orderService.cancel(user, orderId);
        flushAndClear();

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(response.statusDescription()).isEqualTo("주문 취소");
        assertThat(response.canceledAt()).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(stockOf(keyboard)).isEqualTo(10);
        assertThat(stockOf(mouse)).isEqualTo(5);
    }

    @Test
    void cancelByAdmin() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 3))).id();
        flushAndClear();

        orderService.cancel(admin, orderId);
        flushAndClear();

        assertThat(stockOf(keyboard)).isEqualTo(10);
    }

    @Test
    void cancelOfOtherUserThrowsNotFoundAndKeepsOrder() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 3))).id();
        flushAndClear();

        assertThatThrownBy(() -> orderService.cancel(otherUser, orderId))
                .isInstanceOf(OrderNotFoundException.class);
        flushAndClear();

        assertThat(orderService.getOne(user, orderId).status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockOf(keyboard)).isEqualTo(7);
    }

    @Test
    void cancelTwiceThrowsAndRestoresStockOnce() {
        Long keyboard = product("키보드", 30000, 10);
        Long orderId = orderService.create(user, request(line(keyboard, 3))).id();
        orderService.cancel(user, orderId);
        flushAndClear();

        assertThatThrownBy(() -> orderService.cancel(user, orderId))
                .isInstanceOf(InvalidOrderStatusException.class);
        flushAndClear();

        assertThat(stockOf(keyboard)).isEqualTo(10);
    }

    @Test
    void cancelSkipsDeletedProduct() {
        Long keyboard = product("키보드", 30000, 10);
        Long mouse = product("마우스", 15000, 5);
        Long orderId = orderService.create(user, request(line(keyboard, 3), line(mouse, 2))).id();
        entityManager.find(Product.class, keyboard).delete();
        flushAndClear();

        orderService.cancel(user, orderId);
        flushAndClear();

        assertThat(stockOf(mouse)).isEqualTo(5);
        Integer deletedStock = (Integer) entityManager
                .createNativeQuery("select stock from product where id = :id")
                .setParameter("id", keyboard)
                .getSingleResult();
        assertThat(deletedStock).isEqualTo(7);
    }

    @Test
    void cancelMissingThrowsNotFound() {
        assertThatThrownBy(() -> orderService.cancel(user, MISSING_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---------------------------------------------------------------- 도우미

    private static TokenPayload requester(UserRole role) {
        String email = "order-test-" + UUID.randomUUID() + "@example.com";
        return new TokenPayload(email, email, List.of(role));
    }

    private static OrderCreateRequest request(OrderLineRequest... lines) {
        return new OrderCreateRequest(Arrays.asList(lines));
    }

    private static OrderLineRequest line(Long productId, int quantity) {
        return new OrderLineRequest(productId, quantity);
    }

    private Long product(String name, int price, int stock, String... imageUrls) {
        Product product = new Product(name, price, stock);
        for (String url : imageUrls) {
            product.addImage(url);
        }
        entityManager.persist(product);
        flushAndClear();
        return product.getId();
    }

    private int stockOf(Long productId) {
        return entityManager.find(Product.class, productId).getStock();
    }

    private long countOrdersOf(TokenPayload requester) {
        return entityManager
                .createQuery("select count(o) from Order o where o.ordererEmailKey = :emailKey", Long.class)
                .setParameter("emailKey", requester.email().toLowerCase())
                .getSingleResult();
    }

    /**
     * 실행 중에 DB로 보낸 SQL 문 수를 센다. 영속성 컨텍스트를 비운 뒤 실행해 캐시된 엔티티의 영향을 없앤다.
     */
    private long countQueries(Runnable action) {
        flushAndClear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
