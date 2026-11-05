package com.example.demo1.todo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TodoUpdateRequest(
        @NotBlank(message = "내용을 입력해 주세요.") String content,
        @NotNull(message = "완료 여부를 입력해 주세요.") Boolean completed) {
}
