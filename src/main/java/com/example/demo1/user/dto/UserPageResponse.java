package com.example.demo1.user.dto;

import com.example.demo1.user.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * @param page 1부터 시작하는 현재 페이지 번호
 */
public record UserPageResponse(
        List<UserResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static UserPageResponse from(Page<User> users) {
        return new UserPageResponse(
                users.map(UserResponse::from).getContent(),
                // Spring Data의 0부터 시작하는 번호를 요청과 같은 1부터 시작하는 번호로 바꾼다.
                users.getNumber() + 1,
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.hasNext());
    }
}
