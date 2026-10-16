package com.omragul.identity.exception;

public class UserAlreadyExistsException extends IdentityException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}