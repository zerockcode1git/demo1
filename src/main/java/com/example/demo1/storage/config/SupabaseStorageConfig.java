package com.example.demo1.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfig {

    /**
     * Supabase Storage 호출용 클라이언트. 인증 헤더를 기본값으로 넣어 둔다.
     * <p>
     * 새 형식의 secret 키(sb_secret_...)는 JWT가 아니므로 apikey 헤더로만 보낸다.
     * Authorization 헤더에 넣으면 거부된다. 예전 형식의 service_role 키(JWT)는 두 헤더 모두에 넣는다.
     */
    @Bean
    public RestClient supabaseStorageClient(SupabaseStorageProperties properties) {
        String key = properties.serviceRoleKey();
        // Spring Boot 4는 spring-boot-restclient 모듈이 없으면 RestClient.Builder 빈을 만들지 않으므로 직접 생성한다.
        RestClient.Builder client = RestClient.builder()
                .baseUrl(properties.url() + "/storage/v1")
                .defaultHeader("apikey", key);
        if (!key.startsWith("sb_")) {
            client.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        }
        return client.build();
    }
}
