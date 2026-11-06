package com.example.demo1.storage.exception;

/**
 * 업로드할 파일이 비어 있거나 허용하지 않는 형식일 때 발생한다.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
