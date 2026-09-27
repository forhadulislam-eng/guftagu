package com.guftagu.identity.application.exception;

public class ReplayDetectedException extends RuntimeException {
    public ReplayDetectedException(String message) {
        super(message);
    }
}
