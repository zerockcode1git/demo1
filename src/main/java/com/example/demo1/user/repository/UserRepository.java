package com.example.demo1.user.repository;

import com.example.demo1.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 기본 키는 소문자로 바꾼 이메일(emailKey)이다. 조회할 때는 {@link User#toEmailKey}로 바꾼 값을 넘긴다.
 * 목록 조회(findAll(Pageable))는 상속한 메서드를 그대로 쓰고, 권한은 User의 @BatchSize로 한 번에 읽는다.
 * 컬렉션을 fetch join하면서 페이징하면 Hibernate가 전체를 메모리에 올려 자르므로 목록에는 쓰지 않는다.
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 사용자와 권한을 한 번의 쿼리로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.emailKey = :emailKey")
    Optional<User> findWithRolesByEmailKey(@Param("emailKey") String emailKey);
}
