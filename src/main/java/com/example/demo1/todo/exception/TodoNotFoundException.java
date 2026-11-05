package com.example.demo1.todo.exception;

/**
 * 요청한 id의 Todo가 없을 때 발생한다.
 * RuntimeException이므로 @Transactional 메서드 밖으로 던져지면 트랜잭션이 롤백된다.
 */
public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(Long id) {
        super("Todo를 찾을 수 없습니다. id=" + id);
    }
}
