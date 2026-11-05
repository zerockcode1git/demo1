package com.example.demo1.todo.dto;

import com.example.demo1.todo.entity.Todo;
import java.util.List;
import org.springframework.data.domain.Page;

public record TodoPageResponse(
        List<TodoResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static TodoPageResponse from(Page<Todo> todos) {
        return new TodoPageResponse(
                todos.map(TodoResponse::from).getContent(),
                todos.getNumber(),
                todos.getSize(),
                todos.getTotalElements(),
                todos.getTotalPages(),
                todos.hasNext());
    }
}
