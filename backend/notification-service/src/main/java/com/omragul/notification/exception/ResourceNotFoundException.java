package com.omragul.notification.exception;

/**
 * Thrown when a notification resource is not found or does not belong to the user.
 */
public class ResourceNotFoundException extends NotificationException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
