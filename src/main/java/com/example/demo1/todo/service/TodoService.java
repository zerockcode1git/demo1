package com.example.demo1.todo.service;

import com.example.demo1.todo.dto.TodoCreateRequest;
import com.example.demo1.todo.dto.TodoPageRequest;
import com.example.demo1.todo.dto.TodoPageResponse;
import com.example.demo1.todo.dto.TodoResponse;
import com.example.demo1.todo.dto.TodoUpdateRequest;
import com.example.demo1.todo.entity.Todo;
import com.example.demo1.todo.exception.TodoNotFoundException;
import com.example.demo1.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클래스 기본값은 읽기 전용 트랜잭션이고, 데이터를 변경하는 메서드만 @Transactional로 재정의한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse create(TodoCreateRequest request) {
        Todo saved = todoRepository.save(new Todo(request.content()));
        return TodoResponse.from(saved);
    }

    public TodoResponse getOne(Long id) {
        return TodoResponse.from(findTodo(id));
    }

    public TodoPageResponse getAll(TodoPageRequest request) {
        return TodoPageResponse.from(todoRepository.findAll(request.toPageable()));
    }

    @Transactional
    public TodoResponse update(Long id, TodoUpdateRequest request) {
        Todo todo = findTodo(id);
        todo.changeContent(request.content());
        todo.changeCompleted(request.completed());

        // 변경 감지로 저장되지만 updatedAt은 flush 시점에 갱신되므로, 응답 생성 전에 flush한다.
        todoRepository.flush();
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(Long id) {
        todoRepository.delete(findTodo(id));
    }

    private Todo findTodo(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }
}
