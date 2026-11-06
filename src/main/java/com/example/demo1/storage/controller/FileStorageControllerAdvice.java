package com.example.demo1.storage.controller;

import com.example.demo1.storage.config.SupabaseStorageProperties;
import com.example.demo1.storage.dto.FileErrorResponse;
import com.example.demo1.storage.exception.FileStorageException;
import com.example.demo1.storage.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 파일 업로드 관련 예외를 모든 컨트롤러에 공통으로 처리한다.
 * <p>
 * 크기 초과(MaxUploadSizeExceededException)는 컨트롤러가 정해지기 전에 발생하므로,
 * 특정 컨트롤러로 범위를 좁힌 advice에서는 잡히지 않는다. 그래서 범위를 지정하지 않는다.
 * <p>
 * 범위가 전체라 @WebMvcTest 같은 웹 계층 테스트에도 함께 올라온다. 그런 환경에는 설정 빈이 없으므로
 * ObjectProvider로 받아, 없으면 크기 값 없이 안내한다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class FileStorageControllerAdvice {

    private final ObjectProvider<SupabaseStorageProperties> properties;

    @ExceptionHandler(InvalidFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public FileErrorResponse handleInvalidFile(InvalidFileException exception) {
        return new FileErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public FileErrorResponse handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        SupabaseStorageProperties limits = properties.getIfAvailable();
        String message = limits == null
                ? "업로드할 수 있는 크기를 넘었습니다."
                : "파일은 하나당 " + limits.maxFileSize().toMegabytes() + "MB, 요청 전체는 "
                        + limits.maxRequestSize().toMegabytes() + "MB 이하여야 합니다.";
        return new FileErrorResponse(HttpStatus.CONTENT_TOO_LARGE.value(), message);
    }

    @ExceptionHandler(FileStorageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public FileErrorResponse handleStorageFailure(FileStorageException exception) {
        // Supabase 응답 내용은 내부 정보라 로그로만 남긴다.
        log.error(exception.getMessage(), exception);
        return new FileErrorResponse(HttpStatus.BAD_GATEWAY.value(), exception.getMessage());
    }
}
