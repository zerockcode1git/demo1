package com.example.demo1.todo.controller;

import com.example.demo1.security.jwt.dto.TokenPayload;
import com.example.demo1.todo.dto.TodoCreateRequest;
import com.example.demo1.todo.dto.TodoPageRequest;
import com.example.demo1.todo.dto.TodoPageResponse;
import com.example.demo1.todo.dto.TodoResponse;
import com.example.demo1.todo.dto.TodoUpdateRequest;
import com.example.demo1.todo.service.TodoService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Slf4j
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    /**
     * 로그인한 사용자만 허용한다. GUEST(ROLE_GUEST)는 포함하지 않는다.
     * 목록과 조회는 이 조건을 걸지 않으며, 화이트 리스트(GET /api/todos/**)로 GUEST도 호출할 수 있다.
     */
    private static final String LOGIN_REQUIRED = "hasAnyRole('USER', 'MANAGER', 'ADMIN')";

    private final TodoService todoService;

    /**
     * @param user AuthCheckFilter가 JWT에서 꺼내 SecurityContext에 넣은 로그인 사용자 정보.
     *             Swagger 문서에는 요청 파라미터로 보이지 않게 숨긴다.
     */
    @PreAuthorize(LOGIN_REQUIRED)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@Parameter(hidden = true) @AuthenticationPrincipal TokenPayload user,
                               @Valid @RequestBody TodoCreateRequest request) {
        // @PreAuthorize가 로그인 사용자만 통과시키므로 운영에서는 null이 아니다.
        // 보안 필터를 끈 웹 계층 테스트(TodoControllerTest)에서는 null이라 그 경우에도 실패하지 않게 한다.
        log.info("Todo 등록 요청. email={}", user == null ? null : user.email());
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

    @PreAuthorize(LOGIN_REQUIRED)
    @PutMapping("/{id}")
    public TodoResponse update(@PathVariable Long id, @Valid @RequestBody TodoUpdateRequest request) {
        return todoService.update(id, request);
    }

    @PreAuthorize(LOGIN_REQUIRED)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        todoService.delete(id);
    }
}
