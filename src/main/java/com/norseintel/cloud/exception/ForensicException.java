package com.norseintel.cloud.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ForensicException extends RuntimeException {
    private final HttpStatus status;

    public ForensicException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ForensicException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ForensicException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ForensicException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }
}