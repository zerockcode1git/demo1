package com.example.demo1.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * @param page 1부터 시작하는 페이지 번호. 생략하면 1
 * @param size 한 페이지의 항목 수. 생략하면 10
 */
public record UserPageRequest(
        @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    public UserPageRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    /**
     * 최근 가입순. 가입 시각이 같으면 이메일 키순으로 정해 페이지 사이에 순서가 바뀌지 않게 한다.
     */
    public Pageable toPageable() {
        // Spring Data는 페이지 번호가 0부터 시작하므로 1을 뺀다.
        return PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("emailKey")));
    }
}
