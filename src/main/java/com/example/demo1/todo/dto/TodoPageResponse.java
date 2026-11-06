package com.example.demo1.todo.dto;

import com.example.demo1.todo.entity.Todo;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * @param page 1부터 시작하는 현재 페이지 번호
 */
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
                // Spring Data의 0부터 시작하는 번호를 요청과 같은 1부터 시작하는 번호로 바꾼다.
                todos.getNumber() + 1,
                todos.getSize(),
                todos.getTotalElements(),
                todos.getTotalPages(),
                todos.hasNext());
    }
}
