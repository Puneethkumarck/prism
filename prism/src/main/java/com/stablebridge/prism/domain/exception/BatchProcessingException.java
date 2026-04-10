package com.stablebridge.prism.domain.exception;

public class BatchProcessingException extends RuntimeException {

    public BatchProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
