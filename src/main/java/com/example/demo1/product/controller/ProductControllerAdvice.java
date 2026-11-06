package com.example.demo1.product.controller;

import com.example.demo1.product.dto.ProductErrorResponse;
import com.example.demo1.product.exception.InvalidProductImageException;
import com.example.demo1.product.exception.InvalidStockException;
import com.example.demo1.product.exception.ProductNotFoundException;
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
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductControllerAdvice {

    private static final String INVALID_FORMAT_MESSAGE = "요청 값의 형식이 올바르지 않습니다.";

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProductErrorResponse handleNotFound(ProductNotFoundException exception) {
        return new ProductErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage());
    }

    @ExceptionHandler({InvalidProductImageException.class, InvalidStockException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProductErrorResponse handleBadRequest(RuntimeException exception) {
        return new ProductErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProductErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // ?page=abc 같은 변환 실패는 기본 메시지에 내부 타입 정보가 들어 있어 노출하지 않는다.
            String message = error.isBindingFailure() ? INVALID_FORMAT_MESSAGE : error.getDefaultMessage();
            fieldErrors.putIfAbsent(error.getField(), message);
        }
        return new ProductErrorResponse(HttpStatus.BAD_REQUEST.value(), "입력값을 확인해 주세요.", fieldErrors);
    }

    /**
     * 잘못된 JSON 본문, /api/products/abc 같은 경로 변수 변환 실패
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProductErrorResponse handleInvalidFormat(Exception exception) {
        return new ProductErrorResponse(HttpStatus.BAD_REQUEST.value(), INVALID_FORMAT_MESSAGE);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProductErrorResponse handleMissingPart(MissingServletRequestPartException exception) {
        return new ProductErrorResponse(HttpStatus.BAD_REQUEST.value(),
                "'" + exception.getRequestPartName() + "' 항목이 필요합니다.");
    }
}
