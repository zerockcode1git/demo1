package com.example.demo1.todo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo1.support.AuthenticationPrincipalTestConfig;
import com.example.demo1.todo.dto.TodoCreateRequest;
import com.example.demo1.todo.dto.TodoPageRequest;
import com.example.demo1.todo.dto.TodoPageResponse;
import com.example.demo1.todo.dto.TodoResponse;
import com.example.demo1.todo.dto.TodoUpdateRequest;
import com.example.demo1.todo.exception.TodoNotFoundException;
import com.example.demo1.todo.service.TodoService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 계층만 실행한다. TodoService는 가짜 객체라 DB에 접속하지 않는다.
 * 보안 필터(JWT 인증)는 끄고 컨트롤러 동작만 검증한다.
 * {@link AuthenticationPrincipalTestConfig}가 없으면 @AuthenticationPrincipal 파라미터가 있는 등록 요청이 실패한다.
 */
@WebMvcTest(TodoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthenticationPrincipalTestConfig.class)
class TodoControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final TodoResponse TODO = new TodoResponse(1L, "장보기", false, NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoService todoService;

    @Test
    void createReturns201() throws Exception {
        when(todoService.create(new TodoCreateRequest("장보기"))).thenReturn(TODO);

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"장보기\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("장보기"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void getOneReturnsTodo() throws Exception {
        when(todoService.getOne(1L)).thenReturn(TODO);

        mockMvc.perform(get("/api/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getAllUsesDefaultsWhenParametersAreOmitted() throws Exception {
        when(todoService.getAll(new TodoPageRequest(1, 10)))
                .thenReturn(new TodoPageResponse(List.of(TODO), 1, 10, 1, 1, false));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getAllAppliesDefaultSizeWhenOnlyPageIsGiven() throws Exception {
        when(todoService.getAll(new TodoPageRequest(2, 10)))
                .thenReturn(new TodoPageResponse(List.of(), 2, 10, 0, 0, false));

        mockMvc.perform(get("/api/todos").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void updateReturnsTodo() throws Exception {
        TodoResponse updated = new TodoResponse(1L, "운동하기", true, NOW, NOW);
        when(todoService.update(1L, new TodoUpdateRequest("운동하기", true))).thenReturn(updated);

        mockMvc.perform(put("/api/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"운동하기\",\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("운동하기"))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/todos/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(todoService).delete(1L);
    }

    @Test
    void missingTodoReturns404() throws Exception {
        when(todoService.getOne(99L)).thenThrow(new TodoNotFoundException(99L));
        when(todoService.update(eq(99L), any())).thenThrow(new TodoNotFoundException(99L));
        doThrow(new TodoNotFoundException(99L)).when(todoService).delete(99L);

        mockMvc.perform(get("/api/todos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Todo를 찾을 수 없습니다. id=99"));
        mockMvc.perform(put("/api/todos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"운동하기\",\"completed\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/todos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankContentReturns400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.content").value("내용을 입력해 주세요."));

        verifyNoInteractions(todoService);
    }

    @Test
    void missingCompletedReturns400() throws Exception {
        mockMvc.perform(put("/api/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"운동하기\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.completed").value("완료 여부를 입력해 주세요."));

        verifyNoInteractions(todoService);
    }

    @Test
    void invalidPageParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/todos").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("페이지 번호는 1 이상이어야 합니다."));
        mockMvc.perform(get("/api/todos").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("페이지 크기는 1 이상이어야 합니다."));
        mockMvc.perform(get("/api/todos").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("페이지 크기는 100 이하여야 합니다."));
        mockMvc.perform(get("/api/todos").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("요청 값의 형식이 올바르지 않습니다."));

        verifyNoInteractions(todoService);
    }

    @Test
    void malformedRequestsReturn400() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
        mockMvc.perform(get("/api/todos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));

        verifyNoInteractions(todoService);
    }
}
