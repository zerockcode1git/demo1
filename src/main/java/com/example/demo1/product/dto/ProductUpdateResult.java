package com.example.demo1.product.dto;

import java.util.List;

/**
 * 상품 수정 결과. 서비스 사이에서만 쓰며 API 응답으로 내보내지 않는다.
 *
 * @param product          수정된 상품
 * @param removedImageUrls 수정으로 빠진 기존 이미지 URL. 커밋 후 저장소에서 지우는 데 쓴다.
 */
public record ProductUpdateResult(ProductResponse product, List<String> removedImageUrls) {
}
