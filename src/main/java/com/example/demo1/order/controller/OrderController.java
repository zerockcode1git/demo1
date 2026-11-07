package com.example.demo1.order.controller;

import com.example.demo1.order.dto.OrderCreateRequest;
import com.example.demo1.order.dto.OrderPageRequest;
import com.example.demo1.order.dto.OrderPageResponse;
import com.example.demo1.order.dto.OrderResponse;
import com.example.demo1.order.service.OrderService;
import com.example.demo1.security.jwt.dto.TokenPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API. 모든 기능은 로그인한 사용자만 쓸 수 있다.
 * <p>
 * /api/orders/**는 화이트 리스트에 없어 JWT가 없으면 AuthCheckFilter가 먼저 401로 막는다.
 * 메서드마다 @PreAuthorize도 붙여, 화이트 리스트가 바뀌어도 GUEST가 호출하지 못하게 한다.
 * <p>
 * user 파라미터는 AuthCheckFilter가 JWT에서 꺼내 SecurityContext에 넣은 로그인 사용자 정보다.
 * Swagger 문서에는 요청 파라미터로 보이지 않게 숨긴다.
 */
@Tag(name = "주문", description = "주문, 조회, 취소. 로그인 필요")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String LOGIN_REQUIRED = "hasAnyRole('USER', 'MANAGER', 'ADMIN')";

    private final OrderService orderService;

    @Operation(
            summary = "주문",
            description = "상품과 수량 목록으로 주문한다. 같은 상품이 여러 번 있으면 수량을 합친다. "
                    + "하나라도 재고가 부족하면 아무것도 차감하지 않고 409와 부족한 상품 목록을 응답한다.")
    @PreAuthorize(LOGIN_REQUIRED)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Parameter(hidden = true) @AuthenticationPrincipal TokenPayload user,
                                @Valid @RequestBody OrderCreateRequest request) {
        return orderService.create(user, request);
    }

    @Operation(summary = "내 주문 목록", description = "최근 주문순. 페이지 번호는 1부터 시작한다.")
    @PreAuthorize(LOGIN_REQUIRED)
    @GetMapping
    public OrderPageResponse getMine(@Parameter(hidden = true) @AuthenticationPrincipal TokenPayload user,
                                     @ParameterObject @Valid @ModelAttribute OrderPageRequest request) {
        return orderService.getMine(user, request);
    }

    @Operation(summary = "전체 주문 목록", description = "모든 사용자의 주문. ADMIN만 호출할 수 있다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public OrderPageResponse getAll(@ParameterObject @Valid @ModelAttribute OrderPageRequest request) {
        return orderService.getAll(request);
    }

    @Operation(summary = "주문 상세", description = "본인 주문이거나 ADMIN이어야 한다. 다른 사람의 주문은 404를 응답한다.")
    @PreAuthorize(LOGIN_REQUIRED)
    @GetMapping("/{id}")
    public OrderResponse getOne(@Parameter(hidden = true) @AuthenticationPrincipal TokenPayload user,
                                @PathVariable Long id) {
        return orderService.getOne(user, id);
    }

    /**
     * 주문은 지우지 않고 취소 상태로 남기므로 DELETE 대신 POST /{id}/cancel을 쓴다.
     */
    @Operation(
            summary = "주문 취소",
            description = "주문을 취소하고 재고를 되돌린다. 주문은 수정할 수 없으니, 바꾸려면 취소하고 다시 주문한다. "
                    + "본인 주문이거나 ADMIN이어야 하며, 이미 취소된 주문은 409를 응답한다.")
    @PreAuthorize(LOGIN_REQUIRED)
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@Parameter(hidden = true) @AuthenticationPrincipal TokenPayload user,
                                @PathVariable Long id) {
        return orderService.cancel(user, id);
    }
}
