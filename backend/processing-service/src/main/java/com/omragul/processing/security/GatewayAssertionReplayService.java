package com.omragul.processing.security;

/**
 * Contract for atomic distributed replay protection of Gateway assertions.
 */
public interface GatewayAssertionReplayService {

    /**
     * Atomically claims an assertion's unique identifier (jti) in Redis using SET NX EX.
     *
     * @param jti              unique token identifier
     * @param ttlSeconds       remaining accepted assertion lifetime in seconds (positive)
     * @param consumerService  canonical consumer service identity (e.g. "processing-service")
     * @param issuer           assertion issuer identity (e.g. "gateway-service")
     * @return true if the jti was newly claimed; false if the jti already exists (replay detected)
     * @throws GatewayAssertionException if Redis is unavailable, times out, or fails
     */
    boolean claimJti(String jti, long ttlSeconds, String consumerService, String issuer) throws GatewayAssertionException;
}
