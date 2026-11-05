package com.example.demo1.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo1.todo.entity.Todo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * application-local.yaml의 실제 DB에 접속해 실행한다.
 * 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백되어 데이터가 남지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void create() {
        Todo saved = todoRepository.saveAndFlush(new Todo("장보기"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("장보기");
        assertThat(saved.isCompleted()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void read() {
        Long id = saveAndClear("장보기");

        Todo found = todoRepository.findById(id).orElseThrow();

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getContent()).isEqualTo("장보기");
        assertThat(found.isCompleted()).isFalse();
    }

    @Test
    void update() {
        Long id = saveAndClear("장보기");
        Todo before = todoRepository.findById(id).orElseThrow();
        var createdAt = before.getCreatedAt();
        var updatedAt = before.getUpdatedAt();

        before.changeContent("운동하기");
        before.changeCompleted(true);
        todoRepository.flush();
        entityManager.clear();

        Todo after = todoRepository.findById(id).orElseThrow();
        assertThat(after.getContent()).isEqualTo("운동하기");
        assertThat(after.isCompleted()).isTrue();
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void delete() {
        Long id = saveAndClear("장보기");

        todoRepository.deleteById(id);
        todoRepository.flush();
        entityManager.clear();

        assertThat(todoRepository.findById(id)).isEmpty();
    }

    private Long saveAndClear(String content) {
        Long id = todoRepository.saveAndFlush(new Todo(content)).getId();
        entityManager.clear();
        return id;
    }
}
