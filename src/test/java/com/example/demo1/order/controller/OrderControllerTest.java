package com.example.demo1.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.demo1.order.service.OrderService;
import com.example.demo1.support.AuthenticationPrincipalTestConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 웹 계층만 실행한다. 서비스는 가짜 객체라 DB에 접속하지 않는다.
 * 보안 필터(JWT 인증)는 끄고 컨트롤러 동작만 검증한다. 그래서 로그인 사용자(@AuthenticationPrincipal)는 null이고,
 * @PreAuthorize도 적용되지 않는다. 로그인과 권한은 실제 호출로 확인했다(docs/order.md).
 * {@link AuthenticationPrincipalTestConfig}가 없으면 @AuthenticationPrincipal 파라미터에서 요청이 실패한다.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthenticationPrincipalTestConfig.class)
class OrderControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 12, 0);
    private static final OrderResponse ORDER = new OrderResponse(
            1L, OrderStatus.COMPLETED, "주문 완료", 75000, 2, NOW, null,
            List.of(new OrderItemResponse(1L, 10L, "키보드", 30000, 2, "https://x/k.png"),
                    new OrderItemResponse(1L, 20L, "마우스", 15000, 1, null)));
    private static final OrderResponse CANCELED = new OrderResponse(
            1L, OrderStatus.CANCELED, "주문 취소", 75000, 2, NOW, NOW, ORDER.items());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    // ---------------------------------------------------------------- 주문

    @Test
    void createReturns201() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderLineRequest(10L, 2), new OrderLineRequest(20L, 1)));
        when(orderService.create(isNull(), eq(request))).thenReturn(ORDER);

        mockMvc.perform(json(post("/api/orders"),
                        "{\"items\":[{\"productId\":10,\"quantity\":2},{\"productId\":20,\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.statusDescription").value("주문 완료"))
                .andExpect(jsonPath("$.totalPrice").value(75000))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.canceledAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].productId").value(10))
                .andExpect(jsonPath("$.items[0].linePrice").value(60000))
                .andExpect(jsonPath("$.items[0].imageUrl").value("https://x/k.png"))
                .andExpect(jsonPath("$.items[1].imageUrl").doesNotExist());
    }

    /**
     * orderId는 서비스가 주문별로 묶을 때만 쓰는 값이라 응답에 넣지 않는다.
     */
    @Test
    void responseDoesNotExposeItemOrderId() throws Exception {
        when(orderService.getOne(any(), eq(1L))).thenReturn(ORDER);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").doesNotExist());
    }

    @Test
    void createValidationErrorsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(json(post("/api/orders"), "{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.items").value("주문할 상품을 하나 이상 넣어 주세요."));

        mockMvc.perform(json(post("/api/orders"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("주문할 상품을 하나 이상 넣어 주세요."));

        mockMvc.perform(json(post("/api/orders"),
                        "{\"items\":[{\"quantity\":0},{\"productId\":1,\"quantity\":1000},{\"productId\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].productId']").value("상품 ID를 입력해 주세요."))
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("수량은 1 이상이어야 합니다."))
                .andExpect(jsonPath("$.fieldErrors['items[1].quantity']").value("수량은 999 이하여야 합니다."))
                .andExpect(jsonPath("$.fieldErrors['items[2].quantity']").value("수량을 입력해 주세요."));

        verifyNoInteractions(orderService);
    }

    @Test
    void createWithMoreThan50ItemsReturns400() throws Exception {
        String items = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "{\"productId\":" + i + ",\"quantity\":1}")
                .collect(Collectors.joining(",", "{\"items\":[", "]}"));

        mockMvc.perform(json(post("/api/orders"), items))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("한 번에 주문할 수 있는 상품은 50개까지입니다."));

        verifyNoInteractions(orderService);
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(json(post("/api/orders"), "{\"items\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));

        mockMvc.perform(json(post("/api/orders"), "{\"items\":[{\"productId\":\"abc\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void outOfStockReturns409WithShortages() throws Exception {
        when(orderService.create(any(), any())).thenThrow(new OutOfStockException(List.of(
                new StockShortageResponse(20L, "마우스", 1, 5),
                new StockShortageResponse(30L, "모니터", 0, 1))));

        mockMvc.perform(json(post("/api/orders"), "{\"items\":[{\"productId\":20,\"quantity\":5}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("재고가 부족한 상품이 있습니다."))
                .andExpect(jsonPath("$.shortages.length()").value(2))
                .andExpect(jsonPath("$.shortages[0].productId").value(20))
                .andExpect(jsonPath("$.shortages[0].productName").value("마우스"))
                .andExpect(jsonPath("$.shortages[0].stock").value(1))
                .andExpect(jsonPath("$.shortages[0].requested").value(5));
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(orderService.create(any(), any())).thenThrow(new OrderProductNotFoundException(List.of(99L)));

        mockMvc.perform(json(post("/api/orders"), "{\"items\":[{\"productId\":99,\"quantity\":1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("주문할 수 없는 상품입니다. 상품 ID=[99]"))
                .andExpect(jsonPath("$.shortages.length()").value(0));
    }

    // ---------------------------------------------------------------- 목록

    @Test
    void getMineUsesDefaultPage() throws Exception {
        when(orderService.getMine(isNull(), eq(new OrderPageRequest(1, 10))))
                .thenReturn(new OrderPageResponse(List.of(ORDER), 1, 10, 1, 1, false));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getMinePassesPageParameters() throws Exception {
        when(orderService.getMine(any(), eq(new OrderPageRequest(2, 5))))
                .thenReturn(new OrderPageResponse(List.of(), 2, 5, 5, 1, false));

        mockMvc.perform(get("/api/orders").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(orderService).getMine(isNull(), eq(new OrderPageRequest(2, 5)));
    }

    @Test
    void invalidPageParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/orders").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("페이지 번호는 1 이상이어야 합니다."))
                .andExpect(jsonPath("$.fieldErrors.size").value("페이지 크기는 100 이하여야 합니다."));

        mockMvc.perform(get("/api/orders/all").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("요청 값의 형식이 올바르지 않습니다."));

        verifyNoInteractions(orderService);
    }

    /**
     * /all은 /{id}보다 먼저 연결되어야 한다. 그렇지 않으면 "all"을 주문 번호로 바꾸다 400이 된다.
     */
    @Test
    void getAllIsNotTreatedAsOrderId() throws Exception {
        when(orderService.getAll(new OrderPageRequest(1, 10)))
                .thenReturn(new OrderPageResponse(List.of(ORDER), 1, 10, 1, 1, false));

        mockMvc.perform(get("/api/orders/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    // ---------------------------------------------------------------- 상세, 취소

    @Test
    void getOneNotFoundReturns404() throws Exception {
        when(orderService.getOne(any(), eq(99L))).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("주문을 찾을 수 없습니다. id=99"));
    }

    @Test
    void invalidOrderIdReturns400() throws Exception {
        mockMvc.perform(get("/api/orders/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));

        verifyNoInteractions(orderService);
    }

    @Test
    void cancelReturns200() throws Exception {
        when(orderService.cancel(any(), eq(1L))).thenReturn(CANCELED);

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.statusDescription").value("주문 취소"))
                .andExpect(jsonPath("$.canceledAt").exists());
    }

    @Test
    void cancelAlreadyCanceledReturns409() throws Exception {
        when(orderService.cancel(any(), eq(1L)))
                .thenThrow(new InvalidOrderStatusException(OrderStatus.CANCELED, "취소"));

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("주문 취소 상태의 주문은 취소할 수 없습니다."));
    }

    @Test
    void cancelNotFoundReturns404() throws Exception {
        when(orderService.cancel(any(), eq(99L))).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(post("/api/orders/99/cancel"))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
