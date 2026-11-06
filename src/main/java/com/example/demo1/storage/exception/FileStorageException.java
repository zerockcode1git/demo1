package com.example.demo1.storage.exception;

/**
 * Supabase Storage 호출이 실패했을 때 발생한다.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
