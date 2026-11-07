package com.example.demo1.order.dto;

import java.util.List;
import java.util.Map;

/**
 * @param status      HTTP 상태 코드
 * @param message     오류 설명
 * @param fieldErrors 필드별 검증 오류. 없으면 빈 값
 * @param shortages   재고가 부족한 상품. 재고 부족(409)일 때만 값이 있다.
 */
public record OrderErrorResponse(
        int status,
        String message,
        Map<String, String> fieldErrors,
        List<StockShortageResponse> shortages) {

    public OrderErrorResponse(int status, String message) {
        this(status, message, Map.of(), List.of());
    }

    public OrderErrorResponse(int status, String message, Map<String, String> fieldErrors) {
        this(status, message, fieldErrors, List.of());
    }
}
