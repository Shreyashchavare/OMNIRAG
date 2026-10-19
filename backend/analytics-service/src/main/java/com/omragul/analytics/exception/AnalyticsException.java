package com.omragul.analytics.exception;

/**
 * Base exception for the Analytics Service.
 */
public class AnalyticsException extends RuntimeException {

    public AnalyticsException(String message) {
        super(message);
    }

    public AnalyticsException(String message, Throwable cause) {
        super(message, cause);
    }
}
