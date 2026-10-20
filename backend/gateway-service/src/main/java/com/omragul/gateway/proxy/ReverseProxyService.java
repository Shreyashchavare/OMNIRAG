package com.omragul.gateway.proxy;

import com.omragul.gateway.config.GatewayProperties;
import com.omragul.gateway.dto.ErrorResponseDto;
import com.omragul.gateway.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * High-performance HTTP reverse proxy service using Java 21 HttpClient.
 * Streams requests and responses, removes hop-by-hop headers, propagates
 * correlation and user headers, and handles downstream errors safely.
 */
@Slf4j
@Service
public class ReverseProxyService {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final HttpClient httpClient;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    public ReverseProxyService(GatewayProperties gatewayProperties, ObjectMapper objectMapper) {
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(gatewayProperties.getHttp().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Forwards an incoming HTTP request to a target downstream service.
     *
     * @param serviceName    logical service name for logging and error reporting
     * @param targetBaseUrl  target service base URL (e.g. http://identity-service:8080)
     * @param targetPath     target URI path (preserving or rewritten)
     * @param request        incoming HttpServletRequest
     * @param response       outgoing HttpServletResponse
     * @throws IOException on streaming or response writing failure
     */
    public void forward(
            String serviceName,
            String targetBaseUrl,
            String targetPath,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String cleanedBase = targetBaseUrl.replaceAll("/+$", "");
        String rawQuery = request.getQueryString();
        String fullUrl = cleanedBase + targetPath + (rawQuery != null && !rawQuery.isBlank() ? "?" + rawQuery : "");

        URI targetUri;
        try {
            targetUri = URI.create(fullUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid target URI generated: {}", fullUrl);
            sendError(response, HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Invalid request URI", request.getRequestURI());
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofMillis(gatewayProperties.getHttp().getReadTimeoutMs()));

        // Set HTTP method and request body
        String method = request.getMethod().toUpperCase();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            byte[] bodyBytes = request.getInputStream().readAllBytes();
            if (bodyBytes.length > 0) {
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        }

        // Copy incoming headers (excluding hop-by-hop)
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase())) {
                    continue;
                }
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    String value = headerValues.nextElement();
                    try {
                        requestBuilder.header(headerName, value);
                    } catch (IllegalArgumentException ignored) {
                        // Ignore restricted headers that HttpClient manages internally
                    }
                }
            }
        }

        // Forwarded standard headers
        String forwardedProto = request.getHeader("X-Forwarded-Proto") != null
                ? request.getHeader("X-Forwarded-Proto")
                : request.getScheme();
        String forwardedHost = request.getHeader("X-Forwarded-Host") != null
                ? request.getHeader("X-Forwarded-Host")
                : request.getHeader("Host");
        String remoteAddr = request.getRemoteAddr();
        String existingForwardedFor = request.getHeader("X-Forwarded-For");
        String forwardedFor = existingForwardedFor != null
                ? existingForwardedFor + ", " + remoteAddr
                : remoteAddr;

        safeSetHeader(requestBuilder, "X-Forwarded-Proto", forwardedProto);
        if (forwardedHost != null) {
            safeSetHeader(requestBuilder, "X-Forwarded-Host", forwardedHost);
        }
        if (forwardedFor != null) {
            safeSetHeader(requestBuilder, "X-Forwarded-For", forwardedFor);
        }

        // Request correlation ID
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        safeSetHeader(requestBuilder, "X-Request-Id", requestId);

        // Propagate authenticated user context headers (extracted from verified JWT)
        Object userId = request.getAttribute(JwtAuthenticationFilter.ATTR_USER_ID);
        if (userId != null) {
            safeSetHeader(requestBuilder, "X-User-Id", userId.toString());
        }
        Object userRoles = request.getAttribute(JwtAuthenticationFilter.ATTR_USER_ROLES);
        if (userRoles != null) {
            safeSetHeader(requestBuilder, "X-User-Roles", userRoles.toString());
        }
        Object userEmail = request.getAttribute(JwtAuthenticationFilter.ATTR_USER_EMAIL);
        if (userEmail != null) {
            safeSetHeader(requestBuilder, "X-User-Email", userEmail.toString());
        }
        Object userName = request.getAttribute(JwtAuthenticationFilter.ATTR_USER_NAME);
        if (userName != null) {
            safeSetHeader(requestBuilder, "X-User-Name", userName.toString());
        }

        // Execute downstream request
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse<InputStream> downstreamResponse =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Proxied {} {} -> {} ({} ms)", method, targetPath, downstreamResponse.statusCode(), duration);

            // Copy response status
            response.setStatus(downstreamResponse.statusCode());

            // Copy response headers (excluding hop-by-hop)
            for (Map.Entry<String, List<String>> header : downstreamResponse.headers().map().entrySet()) {
                String name = header.getKey();
                if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    continue;
                }
                for (String val : header.getValue()) {
                    response.addHeader(name, val);
                }
            }

            // Stream response body
            try (InputStream in = downstreamResponse.body();
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }

        } catch (HttpConnectTimeoutException | ConnectException e) {
            log.warn("Downstream service '{}' unavailable at {}: {}", serviceName, cleanedBase, e.getMessage());
            sendError(response, HttpStatus.BAD_GATEWAY.value(), "BAD_GATEWAY",
                    "Downstream service unavailable: " + serviceName, request.getRequestURI());

        } catch (HttpTimeoutException e) {
            log.warn("Downstream service '{}' timed out at {}: {}", serviceName, cleanedBase, e.getMessage());
            sendError(response, HttpStatus.GATEWAY_TIMEOUT.value(), "GATEWAY_TIMEOUT",
                    "Downstream service timed out: " + serviceName, request.getRequestURI());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Downstream proxy request interrupted for '{}'", serviceName);
            sendError(response, HttpStatus.BAD_GATEWAY.value(), "BAD_GATEWAY",
                    "Gateway request was interrupted", request.getRequestURI());

        } catch (Exception e) {
            log.error("Downstream proxy error communicating with '{}': {}", serviceName, e.getMessage());
            sendError(response, HttpStatus.BAD_GATEWAY.value(), "BAD_GATEWAY",
                    "Downstream communication failure: " + serviceName, request.getRequestURI());
        }
    }

    private void safeSetHeader(HttpRequest.Builder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            try {
                builder.setHeader(name, value);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String error,
            String message,
            String path
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        ErrorResponseDto errorDto = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorDto));
    }
}
