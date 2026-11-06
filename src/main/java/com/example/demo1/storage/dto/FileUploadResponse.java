package com.example.demo1.storage.dto;

/**
 * @param path 버킷 안의 파일 경로. 삭제할 때 사용한다.
 * @param url  공개 접근 URL. 상품 이미지 경로(imageUrls)로 그대로 쓸 수 있다.
 */
public record FileUploadResponse(String path, String url) {
}
