package com.omragul.analytics.exception;

/**
 * Thrown when an analytics record or resource is not found.
 */
public class ResourceNotFoundException extends AnalyticsException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
