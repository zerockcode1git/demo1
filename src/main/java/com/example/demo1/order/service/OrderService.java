package com.example.demo1.order.service;

import com.example.demo1.order.dto.OrderCreateRequest;
import com.example.demo1.order.dto.OrderItemResponse;
import com.example.demo1.order.dto.OrderLineRequest;
import com.example.demo1.order.dto.OrderPageRequest;
import com.example.demo1.order.dto.OrderPageResponse;
import com.example.demo1.order.dto.OrderResponse;
import com.example.demo1.order.dto.StockShortageResponse;
import com.example.demo1.order.entity.Order;
import com.example.demo1.order.entity.OrderItem;
import com.example.demo1.order.exception.InvalidOrderException;
import com.example.demo1.order.exception.OrderNotFoundException;
import com.example.demo1.order.exception.OrderProductNotFoundException;
import com.example.demo1.order.exception.OutOfStockException;
import com.example.demo1.order.repository.OrderItemRepository;
import com.example.demo1.order.repository.OrderRepository;
import com.example.demo1.product.entity.Product;
import com.example.demo1.product.repository.ProductRepository;
import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.user.entity.User;
import com.example.demo1.user.entity.UserRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문, 조회, 취소. 클래스 기본값은 읽기 전용 트랜잭션이고, 변경하는 메서드만 @Transactional로 재정의한다.
 * <p>
 * 요청한 사용자는 JWT에서 꺼낸 {@link TokenPayload}로 받는다. 조회와 취소는 본인 주문이거나 ADMIN이어야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    /**
     * 주문한다.
     * <ol>
     *     <li>같은 상품의 수량을 합친다.</li>
     *     <li>상품을 id 순서로 잠근다. 주문이 끝날 때까지 다른 주문은 이 상품들의 재고를 바꾸지 못한다.</li>
     *     <li>모든 상품의 재고를 먼저 확인한다. 하나라도 부족하면 아무것도 차감하지 않고 실패한다.</li>
     *     <li>재고를 차감하고, 주문을 완료 상태로 저장한다.</li>
     * </ol>
     *
     * @throws OrderProductNotFoundException 없거나 삭제된 상품이 있는 경우
     * @throws OutOfStockException           재고가 부족한 상품이 있는 경우
     */
    @Transactional
    public OrderResponse create(TokenPayload requester, OrderCreateRequest request) {
        Map<Long, Integer> quantities = mergeQuantities(request.items());
        List<Product> products = lockProducts(quantities);
        validateStock(products, quantities);

        Order order = new Order(emailKeyOf(requester));
        List<OrderItem> items = new ArrayList<>();
        // 응답의 주문 상품 순서가 요청 순서와 같도록 요청 순서로 만든다. 잠금은 이미 id 순서로 끝났다.
        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        quantities.forEach((productId, quantity) -> {
            Product product = productById.get(productId);
            product.decreaseStock(quantity);
            items.add(order.addItem(product.getId(), product.getName(), product.getPrice(), quantity));
        });
        order.complete();

        // 주문이 저장돼야 id가 생기고, 주문 상품이 그 id를 외래 키로 쓴다.
        orderRepository.save(order);
        orderItemRepository.saveAll(items);
        return toResponse(order);
    }

    /**
     * 내 주문 목록. 쿼리는 주문 한 페이지, 전체 개수, 그 페이지 주문들의 주문 상품으로 3번이다.
     */
    public OrderPageResponse getMine(TokenPayload requester, OrderPageRequest request) {
        Page<Order> orders = orderRepository.findPageByOrdererEmailKey(emailKeyOf(requester), request.toPageable());
        return toPageResponse(orders);
    }

    /**
     * 모든 사용자의 주문 목록. ADMIN만 호출하도록 컨트롤러에서 막는다.
     */
    public OrderPageResponse getAll(OrderPageRequest request) {
        return toPageResponse(orderRepository.findAll(request.toPageable()));
    }

    /**
     * @throws OrderNotFoundException 주문이 없거나, 본인 주문이 아니고 ADMIN도 아닌 경우
     */
    public OrderResponse getOne(TokenPayload requester, Long id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireAccess(requester, order);
        return toResponse(order);
    }

    /**
     * 주문을 취소하고 재고를 되돌린다.
     * <p>
     * 주문을 먼저 잠가서, 같은 주문을 동시에 두 번 취소해도 재고는 한 번만 돌아온다.
     * 삭제된 상품은 재고를 되돌리지 않는다.
     *
     * @throws OrderNotFoundException 주문이 없거나, 본인 주문이 아니고 ADMIN도 아닌 경우
     * @throws com.example.demo1.order.exception.InvalidOrderStatusException 이미 취소된 주문인 경우
     */
    @Transactional
    public OrderResponse cancel(TokenPayload requester, Long id) {
        Order order = orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireAccess(requester, order);
        order.cancel();

        Map<Long, Integer> quantities = orderItemRepository.findAllByOrderId(id).stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
        for (Product product : productRepository.findAllByIdInForUpdate(quantities.keySet())) {
            product.increaseStock(quantities.get(product.getId()));
        }

        // 변경 감지로 저장되지만 updatedAt과 canceledAt을 응답에 담기 전에 반영한다.
        orderRepository.flush();
        return toResponse(order);
    }

    /**
     * 요청 순서를 유지하면서 같은 상품의 수량을 합친다.
     */
    private static Map<Long, Integer> mergeQuantities(List<OrderLineRequest> lines) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineRequest line : lines) {
            quantities.merge(line.productId(), line.quantity(), (a, b) -> {
                try {
                    return Math.addExact(a, b);
                } catch (ArithmeticException e) {
                    throw new InvalidOrderException("주문 수량이 너무 큽니다. 상품 ID=" + line.productId());
                }
            });
        }
        return quantities;
    }

    /**
     * 상품을 id 순서로 잠근다. 요청한 상품 중 없거나 삭제된 상품이 있으면 실패한다.
     */
    private List<Product> lockProducts(Map<Long, Integer> quantities) {
        List<Product> products = productRepository.findAllByIdInForUpdate(quantities.keySet());
        if (products.size() != quantities.size()) {
            List<Long> found = products.stream().map(Product::getId).toList();
            List<Long> missing = quantities.keySet().stream().filter(id -> !found.contains(id)).toList();
            throw new OrderProductNotFoundException(missing);
        }
        return products;
    }

    /**
     * 차감하기 전에 모든 상품을 확인해, 부족한 상품을 한 번에 알려 준다.
     * 차감하면서 확인해도 롤백으로 재고는 되돌아가지만, 그러면 처음 부족한 상품 하나만 알 수 있다.
     */
    private static void validateStock(List<Product> products, Map<Long, Integer> quantities) {
        List<StockShortageResponse> shortages = products.stream()
                .filter(product -> product.getStock() < quantities.get(product.getId()))
                .map(product -> new StockShortageResponse(
                        product.getId(), product.getName(), product.getStock(), quantities.get(product.getId())))
                .toList();
        if (!shortages.isEmpty()) {
            throw new OutOfStockException(shortages);
        }
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.of(order, orderItemRepository.findResponsesByOrderIdIn(List.of(order.getId())));
    }

    /**
     * 페이지의 주문 상품을 쿼리 한 번으로 읽어 주문별로 나눠 붙인다.
     */
    private OrderPageResponse toPageResponse(Page<Order> orders) {
        List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItemResponse>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findResponsesByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(OrderItemResponse::orderId));
        return OrderPageResponse.from(
                orders.map(order -> OrderResponse.of(order, itemsByOrderId.getOrDefault(order.getId(), List.of()))));
    }

    /**
     * 본인 주문이 아니고 ADMIN도 아니면 없는 주문으로 처리한다.
     */
    private static void requireAccess(TokenPayload requester, Order order) {
        boolean admin = requester.roles().contains(UserRole.ROLE_ADMIN);
        if (!admin && !order.isOrderedBy(emailKeyOf(requester))) {
            throw new OrderNotFoundException(order.getId());
        }
    }

    private static String emailKeyOf(TokenPayload requester) {
        return User.toEmailKey(requester.email());
    }
}
