package com.omragul.knowledge.security;

public class GatewayAssertionException extends RuntimeException {
    public GatewayAssertionException(String message) {
        super(message);
    }

    public GatewayAssertionException(String message, Throwable cause) {
        super(message, cause);
    }
}
