package com.omragul.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayAssertionReplayTest {

    private static KeyPair testKeyPair;
    private static KeyPair rogueKeyPair;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GatewayAssertionVerifier verifier;
    private MockReplayService mockReplayService;

    @BeforeAll
    static void initKeyPairs() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();
        rogueKeyPair = kpg.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        mockReplayService = new MockReplayService();
        verifier = new GatewayAssertionVerifier(testKeyPair.getPublic(), "knowledge-service", mockReplayService, objectMapper);
    }

    private String buildToken(String sub, String iss, String aud, String rid, String jti, long iat, long exp, PrivateKey key) {
        var builder = Jwts.builder()
                .header().add("typ", "JWT").add("alg", "RS256").and()
                .issuer(iss)
                .subject(sub)
                .claim("roles", "ROLE_USER")
                .claim("rid", rid)
                .claim("mth", "GET")
                .claim("pth", "/api/v1/test")
                .claim("iat", iat)
                .claim("exp", exp);
        if (aud != null) {
            builder.claim("aud", aud);
        }
        if (jti != null) {
            builder.claim("jti", jti);
        }
        return builder.signWith(key, Jwts.SIG.RS256).compact();
    }

    private String buildValidToken(String jti) {
        long now = Instant.now().getEpochSecond();
        return buildToken(UUID.randomUUID().toString(), "gateway-service", "knowledge-service", "req-1",
                jti, now, now + 60, testKeyPair.getPrivate());
    }

    @Test
    @DisplayName("1. Valid assertion with new jti is accepted")
    void testValidAssertionNewJtiAccepted() {
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken(jti);

        GatewayAssertion assertion = verifier.verify(token, "req-1");
        assertThat(assertion).isNotNull();
        assertThat(assertion.jti()).isEqualTo(jti);
        assertThat(mockReplayService.getClaimedKeys()).contains("omragul:replay:knowledge-service:gateway-service:" + jti);
    }

    @Test
    @DisplayName("2. Same assertion submitted twice: second is rejected as replay")
    void testSameAssertionTwiceSecondRejectedAsReplay() {
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken(jti);

        // First verification succeeds
        GatewayAssertion first = verifier.verify(token, "req-1");
        assertThat(first).isNotNull();

        // Second verification with identical token/jti is rejected
        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("replay detected");
    }

    @Test
    @DisplayName("3. Missing jti claim is rejected fail-closed")
    void testMissingJtiRejected() {
        long now = Instant.now().getEpochSecond();
        String tokenNoJti = buildToken(UUID.randomUUID().toString(), "gateway-service", "knowledge-service", "req-1",
                null, now, now + 60, testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenNoJti, "req-1"));
        assertThat(ex.getMessage()).contains("Missing required claim 'jti'");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("4. Null jti claim is rejected fail-closed")
    void testNullJtiRejected() {
        long now = Instant.now().getEpochSecond();
        // Construct raw payload with null jti
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format("{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"knowledge-service\",\"jti\":null,\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\",\"mth\":\"GET\",\"pth\":\"/api/v1/test\"}",
                        UUID.randomUUID(), now, now + 60).getBytes());
        String token = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("Missing required claim 'jti'");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("5. Blank jti claim is rejected fail-closed")
    void testBlankJtiRejected() {
        long now = Instant.now().getEpochSecond();
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.format(
                        "{\"sub\":\"%s\",\"iss\":\"gateway-service\",\"aud\":\"knowledge-service\",\"iat\":%d,\"exp\":%d,\"rid\":\"req-1\",\"jti\":\"   \",\"mth\":\"GET\",\"pth\":\"/api/v1/test\"}",
                        UUID.randomUUID(), now, now + 60)
                        .getBytes(StandardCharsets.UTF_8));
        String tokenBlank = header + "." + payload + ".fakesig";

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenBlank, "req-1"));
        assertThat(ex.getMessage()).contains("Missing or empty assertion identifier (jti)");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("6. Whitespace-padded or malformed jti is rejected fail-closed")
    void testWhitespacePaddedJtiRejected() {
        long now = Instant.now().getEpochSecond();
        String jtiPadded = "  " + UUID.randomUUID() + "  ";
        String tokenPadded = buildToken(UUID.randomUUID().toString(), "gateway-service", "knowledge-service", "req-1",
                jtiPadded, now, now + 60, testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenPadded, "req-1"));
        assertThat(ex.getMessage()).contains("whitespace padding not allowed");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("7. Wrong audience rejected before replay storage is touched")
    void testWrongAudienceRejectedBeforeReplayStorage() {
        long now = Instant.now().getEpochSecond();
        String tokenWrongAud = buildToken(UUID.randomUUID().toString(), "gateway-service", "agent-service", "req-1",
                UUID.randomUUID().toString(), now, now + 60, testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenWrongAud, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion audience");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("8. Invalid signature rejected before replay storage is touched")
    void testInvalidSignatureRejectedBeforeReplayStorage() {
        long now = Instant.now().getEpochSecond();
        String tokenRogue = buildToken(UUID.randomUUID().toString(), "gateway-service", "knowledge-service", "req-1",
                UUID.randomUUID().toString(), now, now + 60, rogueKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenRogue, "req-1"));
        assertThat(ex.getMessage()).contains("Invalid gateway assertion signature");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("9. Expired assertion rejected before replay storage is touched")
    void testExpiredAssertionRejectedBeforeReplayStorage() {
        long now = Instant.now().getEpochSecond();
        String tokenExpired = buildToken(UUID.randomUUID().toString(), "gateway-service", "knowledge-service", "req-1",
                UUID.randomUUID().toString(), now - 3600, now - 60, testKeyPair.getPrivate());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(tokenExpired, "req-1"));
        assertThat(ex.getMessage()).contains("expired");
        assertThat(mockReplayService.getClaimCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("10. Redis key already exists rejected as replay")
    void testRedisKeyAlreadyExistsRejectedAsReplay() {
        String jti = UUID.randomUUID().toString();
        mockReplayService.forceExistingKey("omragul:replay:knowledge-service:gateway-service:" + jti);
        String token = buildValidToken(jti);

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("replay detected");
    }

    @Test
    @DisplayName("11. Redis connection failure rejected fail-closed")
    void testRedisConnectionFailureRejectedFailClosed() {
        mockReplayService.setSimulateConnectionFailure(true);
        String token = buildValidToken(UUID.randomUUID().toString());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("replay verification unavailable");
    }

    @Test
    @DisplayName("12. Redis timeout rejected fail-closed")
    void testRedisTimeoutRejectedFailClosed() {
        mockReplayService.setSimulateTimeout(true);
        String token = buildValidToken(UUID.randomUUID().toString());

        GatewayAssertionException ex = assertThrows(GatewayAssertionException.class,
                () -> verifier.verify(token, "req-1"));
        assertThat(ex.getMessage()).contains("replay verification unavailable");
    }

    @Test
    @DisplayName("13. Correct service namespace used and different consumer namespaces do not collide")
    void testServiceNamespaceIsolation() {
        String jti = UUID.randomUUID().toString();
        // Claim jti under processing-service
        mockReplayService.forceExistingKey("omragul:replay:processing-service:gateway-service:" + jti);

        // knowledge-service should still successfully claim its own namespace for the same jti
        String token = buildValidToken(jti);
        GatewayAssertion assertion = verifier.verify(token, "req-1");
        assertThat(assertion).isNotNull();
        assertThat(mockReplayService.getClaimedKeys()).contains("omragul:replay:knowledge-service:gateway-service:" + jti);
    }

    @Test
    @DisplayName("14. TTL is positive and accounts for remaining token lifetime plus skew buffer")
    void testTtlCalculation() {
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken(jti);

        verifier.verify(token, "req-1");
        Long lastTtl = mockReplayService.getLastTtl();
        assertThat(lastTtl).isNotNull();
        // 60s validity + 10s skew + 5s buffer = ~75s
        assertThat(lastTtl).isBetween(70L, 80L);
    }

    @Test
    @DisplayName("15. Distributed concurrency: 10 concurrent verification requests with same assertion result in exactly one success")
    void testConcurrentVerificationRequestsSameAssertion() throws Exception {
        int threadCount = 10;
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken(jti);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger replayCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    verifier.verify(token, "req-1");
                    successCount.incrementAndGet();
                } catch (GatewayAssertionException e) {
                    if (e.getMessage().contains("replay detected")) {
                        replayCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(replayCount.get()).isEqualTo(threadCount - 1);
        assertThat(errorCount.get()).isEqualTo(0);
    }

    // =========================================================================
    // Mock Replay Service
    // =========================================================================
    static class MockReplayService implements GatewayAssertionReplayService {

        private final Set<String> claimedKeys = Collections.synchronizedSet(new HashSet<>());
        private final AtomicInteger claimCallCount = new AtomicInteger(0);
        private volatile boolean simulateConnectionFailure = false;
        private volatile boolean simulateTimeout = false;
        private volatile Long lastTtl = null;

        @Override
        public boolean claimJti(String jti, long ttlSeconds, String consumerService, String issuer) throws GatewayAssertionException {
            claimCallCount.incrementAndGet();
            if (simulateConnectionFailure) {
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: connection refused");
            }
            if (simulateTimeout) {
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: command timed out");
            }
            this.lastTtl = ttlSeconds;
            String key = String.format("omragul:replay:%s:%s:%s", consumerService, issuer, jti);
            return claimedKeys.add(key); // atomic Set.add mimics SET NX
        }

        public void forceExistingKey(String key) {
            claimedKeys.add(key);
        }

        public Set<String> getClaimedKeys() {
            return claimedKeys;
        }

        public int getClaimCallCount() {
            return claimCallCount.get();
        }

        public void setSimulateConnectionFailure(boolean val) {
            this.simulateConnectionFailure = val;
        }

        public void setSimulateTimeout(boolean val) {
            this.simulateTimeout = val;
        }

        public Long getLastTtl() {
            return lastTtl;
        }
    }
}
