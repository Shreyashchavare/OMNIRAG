package com.omragul.identity.exception;

public class InvalidCredentialsException extends IdentityException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
