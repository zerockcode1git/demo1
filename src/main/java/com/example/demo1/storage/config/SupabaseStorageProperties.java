package com.example.demo1.storage.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * application-local.yaml의 supabase.* 설정.
 *
 * @param url            프로젝트 URL. 예: https://{project-ref}.supabase.co
 * @param serviceRoleKey service_role(secret) 키. 서버에서만 사용하며 git에 올리지 않는다.
 * @param bucket         파일을 저장할 버킷 이름. 공개 URL을 쓰려면 Public 버킷이어야 한다.
 * @param maxFileSize    업로드 파일 하나의 최대 크기. 예: 10MB, 500KB
 * @param maxRequestSize 요청 하나(여러 파일 포함)의 최대 크기. maxFileSize 이상이어야 한다.
 */
@Validated
@ConfigurationProperties(prefix = "supabase")
public record SupabaseStorageProperties(
        @NotBlank String url,
        @NotBlank String serviceRoleKey,
        @NotBlank String bucket,
        @NotNull DataSize maxFileSize,
        @NotNull DataSize maxRequestSize) {

    @AssertTrue(message = "max-request-size는 max-file-size 이상이어야 합니다.")
    public boolean isRequestSizeEnough() {
        return maxFileSize == null || maxRequestSize == null
                || maxRequestSize.toBytes() >= maxFileSize.toBytes();
    }
}
