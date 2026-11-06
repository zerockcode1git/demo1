package com.example.demo1.security.filter;

import com.example.demo1.security.dto.SecurityErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 필터와 보안 예외 처리기에서 오류 응답을 JSON으로 쓴다.
 */
public class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    public SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            // 401 응답에는 어떤 인증 방식을 써야 하는지 알려 주는 헤더를 붙인다(RFC 6750).
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), new SecurityErrorResponse(status, code, message));
    }
}
