package com.example.demo1.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo1.todo.dto.TodoCreateRequest;
import com.example.demo1.todo.dto.TodoPageRequest;
import com.example.demo1.todo.dto.TodoPageResponse;
import com.example.demo1.todo.dto.TodoResponse;
import com.example.demo1.todo.dto.TodoUpdateRequest;
import com.example.demo1.todo.exception.TodoNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * application-local.yaml의 실제 DB에 접속해 실행한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 데이터가 남지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import(TodoService.class)
class TodoServiceTest {

    private static final Long MISSING_ID = -1L;

    @Autowired
    private TodoService todoService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void create() {
        TodoResponse response = todoService.create(new TodoCreateRequest("장보기"));

        assertThat(response.id()).isNotNull();
        assertThat(response.content()).isEqualTo("장보기");
        assertThat(response.completed()).isFalse();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getOne() {
        Long id = createAndClear("장보기");

        TodoResponse response = todoService.getOne(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.content()).isEqualTo("장보기");
    }

    @Test
    void getAllReturnsTenNewestByDefault() {
        List<Long> newestFirst = createTodos(11).reversed();

        TodoPageResponse response = todoService.getAll(new TodoPageRequest(null, null));

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isGreaterThanOrEqualTo(11);
        assertThat(response.totalPages()).isGreaterThanOrEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).extracting(TodoResponse::id)
                .containsExactlyElementsOf(newestFirst.subList(0, 10));
    }

    @Test
    void getAllReturnsRequestedPageAndSize() {
        List<Long> newestFirst = createTodos(6).reversed();

        TodoPageResponse response = todoService.getAll(new TodoPageRequest(1, 3));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.content()).extracting(TodoResponse::id)
                .containsExactlyElementsOf(newestFirst.subList(3, 6));
    }

    @Test
    void update() {
        Long id = createAndClear("장보기");
        TodoResponse before = todoService.getOne(id);
        entityManager.clear();

        TodoResponse response = todoService.update(id, new TodoUpdateRequest("운동하기", true));
        entityManager.clear();

        assertThat(response.content()).isEqualTo("운동하기");
        assertThat(response.completed()).isTrue();
        assertThat(response.updatedAt()).isAfter(before.updatedAt());
        assertThat(todoService.getOne(id)).isEqualTo(response);
    }

    @Test
    void delete() {
        Long id = createAndClear("장보기");

        todoService.delete(id);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> todoService.getOne(id))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    void missingTodoThrowsNotFound() {
        TodoUpdateRequest request = new TodoUpdateRequest("운동하기", true);

        assertThatThrownBy(() -> todoService.getOne(MISSING_ID))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> todoService.update(MISSING_ID, request))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> todoService.delete(MISSING_ID))
                .isInstanceOf(TodoNotFoundException.class);
    }

    private List<Long> createTodos(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> createAndClear("할 일 " + i))
                .toList();
    }

    private Long createAndClear(String content) {
        Long id = todoService.create(new TodoCreateRequest(content)).id();
        entityManager.flush();
        entityManager.clear();
        return id;
    }
}
