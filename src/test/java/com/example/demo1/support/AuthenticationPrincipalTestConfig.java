package com.example.demo1.support;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 보안 설정을 읽지 않는 @WebMvcTest에서 @AuthenticationPrincipal을 처리하게 한다.
 * <p>
 * 이 리졸버가 없으면 Spring MVC가 @AuthenticationPrincipal TokenPayload를 요청 파라미터(@ModelAttribute)로 보고
 * 생성자를 호출한다. roles가 null이라 TokenPayload 생성자에서 NullPointerException이 난다.
 * 리졸버를 등록하면 운영과 같이 SecurityContext에서 사용자를 꺼내고, 인증 정보가 없으면 null을 넘긴다.
 */
@TestConfiguration
public class AuthenticationPrincipalTestConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
    }
}
