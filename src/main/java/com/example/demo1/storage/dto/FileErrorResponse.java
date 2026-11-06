package com.example.demo1.storage.dto;

/**
 * @param status  HTTP 상태 코드
 * @param message 오류 설명
 */
public record FileErrorResponse(int status, String message) {
}
