package com.example.demo1.todo.dto;

import jakarta.validation.constraints.NotBlank;

public record TodoCreateRequest(
        @NotBlank(message = "내용을 입력해 주세요.") String content) {
}
