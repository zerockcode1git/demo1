package com.example.demo1.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * @param page 1부터 시작하는 페이지 번호. 생략하면 1
 * @param size 한 페이지의 주문 수. 생략하면 10
 */
public record OrderPageRequest(
        @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    public OrderPageRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    /**
     * 최근 주문순. (orderer_email_key, id) 인덱스를 타도록 id로 정렬한다.
     */
    public Pageable toPageable() {
        // Spring Data는 페이지 번호가 0부터 시작하므로 1을 뺀다.
        return PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
