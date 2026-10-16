package com.omragul.identity.exception;

// Base Exception
public class IdentityException extends RuntimeException {

    public IdentityException(String message) {
        super(message);
    }

    public IdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}