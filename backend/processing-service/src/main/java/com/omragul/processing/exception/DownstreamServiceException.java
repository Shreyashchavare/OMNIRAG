package com.omragul.processing.exception;

public class DownstreamServiceException extends ProcessingException {
    private final String serviceName;
    private final int statusCode;

    public DownstreamServiceException(String serviceName, int statusCode, String message) {
        super(String.format("Downstream service '%s' returned status %d: %s", serviceName, statusCode, message));
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public DownstreamServiceException(String serviceName, String message, Throwable cause) {
        super(String.format("Failed to communicate with downstream service '%s': %s", serviceName, message), cause);
        this.serviceName = serviceName;
        this.statusCode = 502;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
