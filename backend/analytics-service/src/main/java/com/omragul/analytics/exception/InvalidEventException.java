package com.omragul.analytics.exception;

/**
 * Thrown when an inbound event payload fails business-rule validation
 * (beyond Jakarta Bean Validation which is handled separately by the framework).
 */
public class InvalidEventException extends AnalyticsException {

    public InvalidEventException(String message) {
        super(message);
    }
}
