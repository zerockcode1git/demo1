package com.example.demo1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 연결 정보(host, port, username, password)는 application-local.yaml의 spring.data.redis.*로
 * Spring Boot가 자동 구성한다. 여기서는 키와 값을 어떤 형식으로 저장할지만 정한다.
 */
@Configuration
public class RedisConfig {

    /**
     * 키와 값을 모두 문자열 그대로 저장한다.
     * <p>
     * 기본 RedisTemplate은 JDK 직렬화를 써서 redis-cli로 보면 "\xac\xed\x00..." 같은 바이트가 앞에 붙는다.
     * 문자열 직렬화를 쓰면 저장된 키와 값을 그대로 읽을 수 있다.
     * 빈 이름이 redisTemplate이라 Spring Boot가 만드는 기본 RedisTemplate 대신 이 빈이 쓰인다.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        return template;
    }
}
