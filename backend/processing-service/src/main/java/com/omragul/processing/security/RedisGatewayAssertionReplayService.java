package com.omragul.processing.security;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Distributed Redis-backed replay protection using atomic SET key value NX EX ttl.
 * Fails closed on Redis connection failure, timeout, or unexpected response.
 */
@Slf4j
@Service
public class RedisGatewayAssertionReplayService implements GatewayAssertionReplayService, AutoCloseable {

    public static final String KEY_PREFIX = "omragul:replay";
    public static final String VALUE_MARKER = "1";

    private final String host;
    private final int port;
    private final String password;
    private final int timeoutMs;
    private final boolean enabled;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;

    @Autowired
    public RedisGatewayAssertionReplayService(
            @Value("${gateway.assertion.replay.redis-host:${REDIS_HOST:localhost}}") String host,
            @Value("${gateway.assertion.replay.redis-port:${REDIS_PORT:6379}}") int port,
            @Value("${gateway.assertion.replay.redis-password:${REDIS_PASSWORD:}}") String password,
            @Value("${gateway.assertion.replay.redis-timeout-ms:${REDIS_TIMEOUT_MS:2000}}") int timeoutMs,
            @Value("${gateway.assertion.replay.enabled:true}") boolean enabled
    ) {
        this.host = (host != null && !host.isBlank()) ? host.trim() : "localhost";
        this.port = (port > 0) ? port : 6379;
        this.password = (password != null) ? password.trim() : "";
        this.timeoutMs = (timeoutMs > 0) ? timeoutMs : 2000;
        this.enabled = enabled;
    }

    /**
     * Programmatic constructor for testing with mock or custom connection.
     */
    public RedisGatewayAssertionReplayService(StatefulRedisConnection<String, String> connection) {
        this.host = "localhost";
        this.port = 6379;
        this.password = "";
        this.timeoutMs = 2000;
        this.enabled = true;
        this.connection = connection;
    }

    private synchronized RedisCommands<String, String> getCommands() {
        if (!enabled) {
            throw new GatewayAssertionException("Gateway assertion replay verification unavailable: replay protection is disabled");
        }
        if (connection == null || !connection.isOpen()) {
            try {
                RedisURI.Builder uriBuilder = RedisURI.builder()
                        .withHost(this.host)
                        .withPort(this.port)
                        .withTimeout(Duration.ofMillis(this.timeoutMs));
                if (!this.password.isEmpty()) {
                    uriBuilder.withPassword(this.password.toCharArray());
                }
                RedisURI redisUri = uriBuilder.build();
                this.redisClient = RedisClient.create(redisUri);
                this.connection = this.redisClient.connect();
            } catch (Exception e) {
                log.error("Failed to connect to Redis replay protection host {}:{}: {}", this.host, this.port, e.getMessage());
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: failed to connect to Redis", e);
            }
        }
        return connection.sync();
    }

    @Override
    public boolean claimJti(String jti, long ttlSeconds, String consumerService, String issuer) throws GatewayAssertionException {
        if (jti == null || jti.isBlank()) {
            throw new GatewayAssertionException("Missing or blank jti claim for replay verification");
        }
        if (ttlSeconds <= 0) {
            throw new GatewayAssertionException("Invalid TTL for replay protection: must be positive");
        }
        if (consumerService == null || consumerService.isBlank()) {
            throw new GatewayAssertionException("Missing consumer service identity for replay namespace");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new GatewayAssertionException("Missing issuer identity for replay namespace");
        }

        String key = String.format("%s:%s:%s:%s", KEY_PREFIX, consumerService.trim(), issuer.trim(), jti.trim());

        try {
            RedisCommands<String, String> commands = getCommands();
            SetArgs args = SetArgs.Builder.nx().ex(ttlSeconds);
            String response = commands.set(key, VALUE_MARKER, args);
            // Redis returns "OK" if key was set (NX condition met), null if key already existed
            if ("OK".equalsIgnoreCase(response)) {
                return true;
            } else if (response == null) {
                // Key already existed -> Replay detected!
                log.warn("Replay detected for consumer '{}' with jti '{}'", consumerService, sanitizeForLog(jti));
                return false;
            } else {
                log.error("Unexpected Redis response during replay claim for key '{}': {}", key, response);
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: unexpected Redis response");
            }
        } catch (GatewayAssertionException e) {
            throw e;
        } catch (RedisException e) {
            log.error("Redis error during replay check for consumer '{}': {}", consumerService, e.getMessage());
            throw new GatewayAssertionException("Gateway assertion replay verification unavailable: Redis error", e);
        } catch (Exception e) {
            log.error("Unexpected failure during replay check for consumer '{}': {}", consumerService, e.getMessage());
            throw new GatewayAssertionException("Gateway assertion replay verification unavailable", e);
        }
    }

    private static String sanitizeForLog(String val) {
        if (val == null) return "null";
        return val.replaceAll("[\r\n]", "_");
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {}
            connection = null;
        }
        if (redisClient != null) {
            try {
                redisClient.shutdown();
            } catch (Exception ignored) {}
            redisClient = null;
        }
    }
}
