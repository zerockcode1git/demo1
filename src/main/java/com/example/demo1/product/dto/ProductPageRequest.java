package com.example.demo1.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * @param page    1부터 시작하는 페이지 번호. 생략하면 1
 * @param size    한 페이지의 항목 수. 생략하면 10
 * @param keyword 상품명 검색어. 대소문자 구분 없이 이름에 포함된 상품을 찾는다. 생략하거나 공백이면 전체 조회
 */
public record ProductPageRequest(
        @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size,
        @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") String keyword) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    public ProductPageRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        keyword = keyword == null ? "" : keyword.strip();
    }

    /**
     * 상품명 LIKE 검색 패턴. 검색어의 \, %, _는 문자 그대로 찾도록 이스케이프한다.
     * 검색어가 없으면 "%%"가 되어 전체와 일치한다.
     */
    public String toNamePattern() {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public Pageable toPageable() {
        // Spring Data는 페이지 번호가 0부터 시작하므로 1을 뺀다.
        return PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
