package com.example.demo1.todo.dto;

import com.example.demo1.todo.entity.Todo;
import java.time.LocalDateTime;

public record TodoResponse(
        Long id,
        String content,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getContent(),
                todo.isCompleted(),
                todo.getCreatedAt(),
                todo.getUpdatedAt());
    }
}
