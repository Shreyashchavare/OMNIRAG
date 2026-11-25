package com.omragul.processing.exception;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

public class DownstreamServiceException extends ProcessingException {

    public enum DownstreamErrorType {
        CONNECTION_FAILURE,
        CONNECTION_TIMEOUT,
        RESPONSE_TIMEOUT,
        CLIENT_ERROR_4XX,
        SERVER_ERROR_5XX,
        MALFORMED_RESPONSE,
        UPLOAD_SIZE_EXCEEDED,
        UNSPECIFIED
    }

    private final String serviceName;
    private final int statusCode;
    private final boolean isRetryable;
    private final DownstreamErrorType errorType;

    public DownstreamServiceException(String serviceName, int statusCode, String message, boolean isRetryable, DownstreamErrorType errorType) {
        super(String.format("Downstream service '%s' returned status %d: %s", serviceName, statusCode, sanitizeMessage(message)));
        this.serviceName = serviceName;
        this.statusCode = statusCode;
        this.isRetryable = isRetryable;
        this.errorType = errorType != null ? errorType : deriveErrorType(statusCode);
    }

    public DownstreamServiceException(String serviceName, int statusCode, String message, boolean isRetryable) {
        this(serviceName, statusCode, message, isRetryable, deriveErrorType(statusCode));
    }

    public DownstreamServiceException(String serviceName, int statusCode, String message) {
        this(serviceName, statusCode, message, isStatusCodeRetryable(statusCode), deriveErrorType(statusCode));
    }

    public DownstreamServiceException(String serviceName, String message, Throwable cause, DownstreamErrorType errorType) {
        super(String.format("Failed to communicate with downstream service '%s': %s", serviceName, sanitizeMessage(message)), cause);
        this.serviceName = serviceName;
        this.statusCode = 502;
        this.isRetryable = true;
        this.errorType = errorType != null ? errorType : deriveErrorTypeFromCause(cause, message);
    }

    public DownstreamServiceException(String serviceName, String message, Throwable cause) {
        this(serviceName, message, cause, deriveErrorTypeFromCause(cause, message));
    }

    /**
     * Determines retry classification:
     * - 408 (Request Timeout) and 429 (Too Many Requests / Rate Limit) are transient/retryable.
     * - Other 4xx responses (400, 401, 403, 404, 422) are permanent client errors (not retryable).
     * - 5xx responses (500, 502, 503, 504) are transient/retryable.
     */
    public static boolean isStatusCodeRetryable(int statusCode) {
        if (statusCode == 408 || statusCode == 429) {
            return true;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return false;
        }
        return true;
    }

    public static DownstreamErrorType deriveErrorType(int statusCode) {
        if (statusCode == 408) {
            return DownstreamErrorType.RESPONSE_TIMEOUT;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return DownstreamErrorType.CLIENT_ERROR_4XX;
        }
        if (statusCode >= 500) {
            return DownstreamErrorType.SERVER_ERROR_5XX;
        }
        return DownstreamErrorType.UNSPECIFIED;
    }

    public static DownstreamErrorType deriveErrorTypeFromCause(Throwable cause, String message) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof HttpConnectTimeoutException) {
                return DownstreamErrorType.CONNECTION_TIMEOUT;
            }
            if (current instanceof ConnectException) {
                return DownstreamErrorType.CONNECTION_FAILURE;
            }
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return DownstreamErrorType.RESPONSE_TIMEOUT;
            }
            current = current.getCause();
        }
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("connect timed out") || lower.contains("connection timed out")) {
                return DownstreamErrorType.CONNECTION_TIMEOUT;
            }
            if (lower.contains("read timed out") || lower.contains("socket timeout") || lower.contains("timed out") || lower.contains("timeout")) {
                return DownstreamErrorType.RESPONSE_TIMEOUT;
            }
            if (lower.contains("connection refused") || lower.contains("connect")) {
                return DownstreamErrorType.CONNECTION_FAILURE;
            }
        }
        return DownstreamErrorType.CONNECTION_FAILURE;
    }

    public static DownstreamServiceException fromResourceAccessException(String serviceName, ResourceAccessException e) {
        DownstreamErrorType type = deriveErrorTypeFromCause(e, e.getMessage());
        String msg;
        switch (type) {
            case CONNECTION_TIMEOUT:
                msg = "Connection timeout connecting to " + serviceName;
                break;
            case RESPONSE_TIMEOUT:
                msg = "Response timeout waiting for " + serviceName;
                break;
            case CONNECTION_FAILURE:
            default:
                msg = "Network failure connecting to " + serviceName;
                break;
        }
        return new DownstreamServiceException(serviceName, msg, e, type);
    }

    public static DownstreamServiceException fromHttpClientErrorException(String serviceName, HttpClientErrorException e) {
        return new DownstreamServiceException(serviceName, e.getStatusCode().value(), e.getResponseBodyAsString(), false, DownstreamErrorType.CLIENT_ERROR_4XX);
    }

    public static DownstreamServiceException fromHttpServerErrorException(String serviceName, HttpServerErrorException e) {
        return new DownstreamServiceException(serviceName, e.getStatusCode().value(), e.getResponseBodyAsString(), true, DownstreamErrorType.SERVER_ERROR_5XX);
    }

    public static DownstreamServiceException fromMalformedResponse(String serviceName, String message, Throwable cause) {
        return new DownstreamServiceException(serviceName, "Malformed response from " + serviceName + ": " + sanitizeMessage(message), cause, DownstreamErrorType.MALFORMED_RESPONSE);
    }

    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No detail provided";
        }
        // Truncate overly long error payloads (e.g. HTML pages, stack traces) to prevent log pollution or client leakage
        if (message.length() > 250) {
            return message.substring(0, 250) + "... [truncated]";
        }
        return message;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return isRetryable;
    }

    public DownstreamErrorType getErrorType() {
        return errorType;
    }

    public boolean isTimeout() {
        return errorType == DownstreamErrorType.CONNECTION_TIMEOUT || errorType == DownstreamErrorType.RESPONSE_TIMEOUT;
    }

    public boolean isConnectionFailure() {
        return errorType == DownstreamErrorType.CONNECTION_FAILURE;
    }

    public boolean isClientError() {
        return errorType == DownstreamErrorType.CLIENT_ERROR_4XX;
    }

    public boolean isServerError() {
        return errorType == DownstreamErrorType.SERVER_ERROR_5XX;
    }
}
