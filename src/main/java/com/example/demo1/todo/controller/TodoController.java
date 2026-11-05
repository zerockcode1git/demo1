package com.example.demo1.todo.controller;

import com.example.demo1.todo.dto.TodoCreateRequest;
import com.example.demo1.todo.dto.TodoPageRequest;
import com.example.demo1.todo.dto.TodoPageResponse;
import com.example.demo1.todo.dto.TodoResponse;
import com.example.demo1.todo.dto.TodoUpdateRequest;
import com.example.demo1.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@Valid @RequestBody TodoCreateRequest request) {
        return todoService.create(request);
    }

    @GetMapping("/{id}")
    public TodoResponse getOne(@PathVariable Long id) {
        return todoService.getOne(id);
    }

    @GetMapping
    public TodoPageResponse getAll(@ParameterObject @Valid @ModelAttribute TodoPageRequest request) {
        return todoService.getAll(request);
    }

    @PutMapping("/{id}")
    public TodoResponse update(@PathVariable Long id, @Valid @RequestBody TodoUpdateRequest request) {
        return todoService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        todoService.delete(id);
    }
}
