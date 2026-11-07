package com.example.demo1.order.controller;

import com.example.demo1.order.dto.OrderErrorResponse;
import com.example.demo1.order.exception.InvalidOrderException;
import com.example.demo1.order.exception.InvalidOrderStatusException;
import com.example.demo1.order.exception.OrderNotFoundException;
import com.example.demo1.order.exception.OrderProductNotFoundException;
import com.example.demo1.order.exception.OutOfStockException;
import com.example.demo1.product.exception.InsufficientStockException;
import com.example.demo1.product.exception.InvalidStockException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderControllerAdvice {

    private static final String INVALID_FORMAT_MESSAGE = "요청 값의 형식이 올바르지 않습니다.";

    @ExceptionHandler({OrderNotFoundException.class, OrderProductNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public OrderErrorResponse handleNotFound(RuntimeException exception) {
        return new OrderErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public OrderErrorResponse handleOutOfStock(OutOfStockException exception) {
        return new OrderErrorResponse(
                HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of(), exception.getShortages());
    }

    /**
     * 이미 취소된 주문을 취소하는 등 지금 상태와 맞지 않는 요청.
     * InsufficientStockException은 재고를 미리 확인하므로 보통 나지 않지만, 나더라도 409로 응답한다.
     */
    @ExceptionHandler({InvalidOrderStatusException.class, InsufficientStockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public OrderErrorResponse handleConflict(RuntimeException exception) {
        return new OrderErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage());
    }

    @ExceptionHandler({InvalidOrderException.class, InvalidStockException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OrderErrorResponse handleBadRequest(RuntimeException exception) {
        return new OrderErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OrderErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // ?page=abc 같은 변환 실패는 기본 메시지에 내부 타입 정보가 들어 있어 노출하지 않는다.
            String message = error.isBindingFailure() ? INVALID_FORMAT_MESSAGE : error.getDefaultMessage();
            fieldErrors.putIfAbsent(error.getField(), message);
        }
        return new OrderErrorResponse(HttpStatus.BAD_REQUEST.value(), "입력값을 확인해 주세요.", fieldErrors);
    }

    /**
     * 잘못된 JSON 본문, /api/orders/abc 같은 경로 변수 변환 실패
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OrderErrorResponse handleInvalidFormat(Exception exception) {
        return new OrderErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_FORMAT_MESSAGE);
    }
}
