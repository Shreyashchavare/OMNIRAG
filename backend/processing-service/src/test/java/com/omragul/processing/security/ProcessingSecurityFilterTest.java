package com.omragul.processing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omragul.processing.dto.response.ErrorResponseDto;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authoritative unit tests for SecurityContextFilter in processing-service.
 * Tests filter behavior, gateway assertion verification, internal service credentials,
 * direct header rejections, replay protection fail-closed semantics, and context cleanup.
 */
class ProcessingSecurityFilterTest {

    private static final String TEST_SECRET = "test-processing-internal-secret-987654";
    private static KeyPair testKeyPair;
    private static KeyPair rogueKeyPair;

    private SecurityContextFilter filter;
    private InternalAuthService internalAuthService;
    private GatewayAssertionVerifier gatewayAssertionVerifier;
    private MockReplayService mockReplayService;
    private SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void initKeyPairs() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();
        rogueKeyPair = kpg.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        internalAuthService = new InternalAuthService(TEST_SECRET);
        mockReplayService = new MockReplayService();
        gatewayAssertionVerifier = new GatewayAssertionVerifier(
                testKeyPair.getPublic(),
                "processing-service",
                mockReplayService,
                objectMapper
        );
        securityAuditLogger = new SecurityAuditLogger();
        filter = new SecurityContextFilter(
                internalAuthService,
                gatewayAssertionVerifier,
                securityAuditLogger,
                objectMapper
        );
        UserSecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserSecurityContextHolder.clear();
    }

    // =========================================================================
    // Helper: RS256 Token Builder
    // =========================================================================

    private String createAssertion(
            String userId,
            String roles,
            String requestId,
            String targetAudience,
            String issuer,
            String method,
            String path,
            String jti,
            long iatDeltaSeconds,
            long expDeltaSeconds,
            KeyPair keyPair
    ) {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
                .header()
                    .add("typ", "JWT")
                    .add("alg", "RS256")
                .and()
                .id(jti != null ? jti : UUID.randomUUID().toString())
                .issuer(issuer != null ? issuer : "gateway-service")
                .audience().single(targetAudience != null ? targetAudience : "processing-service")
                .subject(userId != null ? userId : UUID.randomUUID().toString())
                .claim("roles", roles != null ? roles : "ROLE_USER")
                .claim("rid", requestId != null ? requestId : "req-" + UUID.randomUUID())
                .claim("iat", now + iatDeltaSeconds)
                .claim("exp", now + expDeltaSeconds)
                .claim("mth", method != null ? method : "POST")
                .claim("pth", path != null ? path : "/api/v1/processing/jobs")
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String createValidAssertion(UUID userId, String roles, String requestId, String method, String path, String jti) {
        return createAssertion(
                userId.toString(),
                roles,
                requestId,
                "processing-service",
                "gateway-service",
                method,
                path,
                jti,
                0,
                60,
                testKeyPair
        );
    }

    // =========================================================================
    // 1. Valid Gateway Assertion Path
    // =========================================================================

    @Test
    @DisplayName("1. Valid RS256 Gateway assertion succeeds, populates UserSecurityContext, and executes filter chain")
    void testValidGatewayAssertion_SucceedsAndEstablishesUserContext() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String roles = "ROLE_USER,ROLE_OPERATOR";
        String requestId = "req-valid-001";
        String jti = UUID.randomUUID().toString();
        String path = "/api/v1/processing/jobs";

        String token = createValidAssertion(userId, roles, requestId, "POST", path, jti);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, requestId);
        request.addHeader(SecurityContextFilter.HEADER_USER_ID, userId.toString());

        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] chainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            chainExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context, "UserSecurityContext must be established during filter chain execution");
            assertEquals(userId, context.getUserId());
            assertThat(context.getRoles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_OPERATOR");
            assertFalse(context.isInternalService());
            assertFalse(context.isAdmin());
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, filterChain);

        assertTrue(chainExecuted[0], "Downstream filter chain must be invoked");
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext(), "Context must be cleared in finally block");
        assertThat(mockReplayService.getClaimedKeys()).hasSize(1);
    }

    // =========================================================================
    // 2. Missing Authentication Credentials
    // =========================================================================

    @Test
    @DisplayName("2. Missing authentication credentials returns 401 Unauthorized")
    void testMissingCredentials_Returns401Unauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("UNAUTHORIZED", errorDto.getError());
        assertEquals("Authentication required: missing user identity or internal service credentials", errorDto.getMessage());
        assertEquals("/api/v1/processing/jobs", errorDto.getPath());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 3. Direct Untrusted X-User-Id Header
    // =========================================================================

    @Test
    @DisplayName("3. Direct untrusted X-User-Id header without assertion returns 401 Unauthorized")
    void testDirectUntrustedUserId_Returns401Unauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        request.addHeader(SecurityContextFilter.HEADER_USER_ID, UUID.randomUUID().toString());
        request.addHeader(SecurityContextFilter.HEADER_USER_ROLES, "ROLE_ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("UNAUTHORIZED", errorDto.getError());
        assertEquals("Direct client identity headers rejected: gateway assertion required", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 4. Internal Service Credentials Path
    // =========================================================================

    @Test
    @DisplayName("4a. Valid internal service secret via X-Internal-Secret establishes internal context")
    void testValidInternalSecret_XInternalSecret_EstablishesInternalContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        request.addHeader(SecurityContextFilter.HEADER_INTERNAL_SECRET, TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] chainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            chainExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context);
            assertTrue(context.isInternalService());
            assertNull(context.getUserId());
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, filterChain);

        assertTrue(chainExecuted[0]);
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("4b. Valid internal service secret via X-Internal-Service-Secret establishes internal context")
    void testValidInternalSecret_XInternalServiceSecret_EstablishesInternalContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/processing/jobs/status");
        request.addHeader(SecurityContextFilter.HEADER_INTERNAL_SERVICE_SECRET, TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] chainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            chainExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context);
            assertTrue(context.isInternalService());
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, filterChain);

        assertTrue(chainExecuted[0]);
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("5. Invalid internal service secret returns 401 Unauthorized")
    void testInvalidInternalSecret_Returns401Unauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        request.addHeader(SecurityContextFilter.HEADER_INTERNAL_SECRET, "wrong-unauthorized-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("UNAUTHORIZED", errorDto.getError());
        assertEquals("Invalid internal service credentials", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 6. Cryptographic Signature Validation
    // =========================================================================

    @Test
    @DisplayName("6. Assertion signed with rogue key returns 401 Unauthorized")
    void testRogueSignature_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String path = "/api/v1/processing/jobs";

        String rogueToken = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-rogue",
                "processing-service",
                "gateway-service",
                "POST",
                path,
                jti,
                0,
                60,
                rogueKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, rogueToken);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-rogue");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("UNAUTHORIZED", errorDto.getError());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
        assertThat(mockReplayService.getClaimedKeys()).isEmpty();
    }

    // =========================================================================
    // 7. Audience Validation
    // =========================================================================

    @Test
    @DisplayName("7. Assertion with wrong audience returns 401 Unauthorized")
    void testWrongAudience_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        String wrongAudToken = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-wrong-aud",
                "knowledge-service", // Mismatched audience
                "gateway-service",
                "POST",
                path,
                UUID.randomUUID().toString(),
                0,
                60,
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, wrongAudToken);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-wrong-aud");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 8. Issuer Validation
    // =========================================================================

    @Test
    @DisplayName("8. Assertion with wrong issuer returns 401 Unauthorized")
    void testWrongIssuer_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        String wrongIssToken = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-wrong-iss",
                "processing-service",
                "rogue-issuer-service", // Mismatched issuer
                "POST",
                path,
                UUID.randomUUID().toString(),
                0,
                60,
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, wrongIssToken);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-wrong-iss");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 9. Expiry Validation
    // =========================================================================

    @Test
    @DisplayName("9. Expired assertion outside clock skew returns 401 Unauthorized")
    void testExpiredAssertion_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        String expiredToken = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-expired",
                "processing-service",
                "gateway-service",
                "POST",
                path,
                UUID.randomUUID().toString(),
                -120, // iat = 2 min ago
                -30,  // exp = 30s ago (beyond 10s skew)
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, expiredToken);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-expired");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 10. Future Timestamp Validation (iat)
    // =========================================================================

    @Test
    @DisplayName("10. Future iat timestamp beyond clock skew returns 401 Unauthorized")
    void testFutureIat_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        String futureToken = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-future",
                "processing-service",
                "gateway-service",
                "POST",
                path,
                UUID.randomUUID().toString(),
                60,  // iat = 60s in future (beyond 10s skew)
                120,
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, futureToken);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-future");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 11. Method Binding Mismatch (mth)
    // =========================================================================

    @Test
    @DisplayName("11. HTTP method mismatch between assertion and request returns 401 Unauthorized")
    void testMethodMismatch_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        // Assertion bound to GET, but incoming request is POST
        String token = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-method-mismatch",
                "processing-service",
                "gateway-service",
                "GET",
                path,
                UUID.randomUUID().toString(),
                0,
                60,
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-method-mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
        assertThat(mockReplayService.getClaimedKeys()).isEmpty();
    }

    // =========================================================================
    // 12. Path Binding Mismatch (pth)
    // =========================================================================

    @Test
    @DisplayName("12. HTTP path mismatch between assertion and request returns 401 Unauthorized")
    void testPathMismatch_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();

        // Assertion bound to /api/v1/documents, incoming request is /api/v1/processing/jobs
        String token = createAssertion(
                userId.toString(),
                "ROLE_USER",
                "req-path-mismatch",
                "processing-service",
                "gateway-service",
                "POST",
                "/api/v1/documents",
                UUID.randomUUID().toString(),
                0,
                60,
                testKeyPair
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-path-mismatch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
        assertThat(mockReplayService.getClaimedKeys()).isEmpty();
    }

    // =========================================================================
    // 13. Replay Protection (JTI Duplication)
    // =========================================================================

    @Test
    @DisplayName("13. Replayed JTI returns 401 Unauthorized on subsequent request")
    void testReplayedJti_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String path = "/api/v1/processing/jobs";

        String token = createValidAssertion(userId, "ROLE_USER", "req-replay-1", "POST", path, jti);

        // First request: Should succeed
        MockHttpServletRequest request1 = new MockHttpServletRequest("POST", path);
        request1.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request1.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-replay-1");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        final boolean[] chainExecuted1 = {false};
        FilterChain filterChain1 = (req, res) -> {
            chainExecuted1[0] = true;
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request1, response1, filterChain1);

        assertTrue(chainExecuted1[0], "First request with new JTI must succeed");
        assertEquals(200, response1.getStatus());

        // Second request with IDENTICAL token (same JTI): Must be rejected as replay
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", path);
        request2.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request2.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-replay-2");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        MockFilterChain filterChain2 = new MockFilterChain();

        filter.doFilter(request2, response2, filterChain2);

        assertEquals(401, response2.getStatus(), "Second request with same JTI must be rejected as replay");
        ErrorResponseDto errorDto = objectMapper.readValue(response2.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 14. Redis Outage Fail-Closed
    // =========================================================================

    @Test
    @DisplayName("14a. Redis connection failure during replay check fails closed with 401 Unauthorized")
    void testRedisConnectionFailure_FailsClosedWith401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String path = "/api/v1/processing/jobs";
        String token = createValidAssertion(userId, "ROLE_USER", "req-redis-fail", "POST", path, jti);

        mockReplayService.setSimulateConnectionFailure(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-redis-fail");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("14b. Redis timeout during replay check fails closed with 401 Unauthorized")
    void testRedisTimeout_FailsClosedWith401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String path = "/api/v1/processing/jobs";
        String token = createValidAssertion(userId, "ROLE_USER", "req-redis-timeout", "POST", path, jti);

        mockReplayService.setSimulateTimeout(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-redis-timeout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 15. Non-Processing Paths Bypass Filter
    // =========================================================================

    @Test
    @DisplayName("15. Non-processing path (/health) bypasses security filter via shouldNotFilter")
    void testNonProcessingPath_BypassesFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] chainExecuted = {false};
        FilterChain filterChain = (req, res) -> {
            chainExecuted[0] = true;
            assertNull(UserSecurityContextHolder.getContext(), "No security context established for unauthenticated path");
            ((HttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, filterChain);

        assertTrue(chainExecuted[0], "Chain must be executed for bypassed paths");
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 16 & 17. Security Context Cleanup Guarantees
    // =========================================================================

    @Test
    @DisplayName("16. Security context is cleared after successful request completion")
    void testContextCleanup_AfterSuccessfulRequest() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";
        String token = createValidAssertion(userId, "ROLE_USER", "req-cleanup-1", "POST", path, UUID.randomUUID().toString());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-cleanup-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext(), "Security context must be null after filter completion");
    }

    @Test
    @DisplayName("17. Security context is cleared even when downstream filter throws ServletException")
    void testContextCleanup_AfterDownstreamFilterException() {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";
        String token = createValidAssertion(userId, "ROLE_USER", "req-cleanup-2", "POST", path, UUID.randomUUID().toString());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-cleanup-2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            assertNotNull(UserSecurityContextHolder.getContext());
            throw new ServletException("Downstream filter failed intentionally");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, throwingChain));
        assertNull(UserSecurityContextHolder.getContext(), "Security context must be cleaned up in finally block despite exception");
    }

    // =========================================================================
    // 18. Request ID Correlation Verification
    // =========================================================================

    @Test
    @DisplayName("18. Request ID mismatch between assertion rid and header X-Request-Id returns 401 Unauthorized")
    void testRequestIdMismatch_Returns401Unauthorized() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String path = "/api/v1/processing/jobs";

        // Assertion has rid="req-expected", but request sends X-Request-Id="req-different"
        String token = createValidAssertion(userId, "ROLE_USER", "req-expected", "POST", path, UUID.randomUUID().toString());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, token);
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-different");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // 19. Malformed Assertion Token
    // =========================================================================

    @Test
    @DisplayName("19. Malformed assertion token string returns 401 Unauthorized")
    void testMalformedAssertion_Returns401Unauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/processing/jobs");
        request.addHeader(SecurityContextFilter.HEADER_GATEWAY_ASSERTION, "not-a-valid-jwt-token");
        request.addHeader(SecurityContextFilter.HEADER_REQUEST_ID, "req-malformed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        ErrorResponseDto errorDto = objectMapper.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertEquals(401, errorDto.getStatus());
        assertEquals("Invalid or expired gateway assertion", errorDto.getMessage());
        assertNull(UserSecurityContextHolder.getContext());
    }

    // =========================================================================
    // In-Memory Mock Replay Service (Deterministic, No Real Redis)
    // =========================================================================

    static class MockReplayService implements GatewayAssertionReplayService {

        private final Set<String> claimedKeys = Collections.synchronizedSet(new HashSet<>());
        private final AtomicInteger claimCallCount = new AtomicInteger(0);
        private volatile boolean simulateConnectionFailure = false;
        private volatile boolean simulateTimeout = false;

        @Override
        public boolean claimJti(String jti, long ttlSeconds, String consumerService, String issuer) throws GatewayAssertionException {
            claimCallCount.incrementAndGet();
            if (simulateConnectionFailure) {
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: connection refused");
            }
            if (simulateTimeout) {
                throw new GatewayAssertionException("Gateway assertion replay verification unavailable: command timed out");
            }
            String key = String.format("omragul:replay:%s:%s:%s", consumerService, issuer, jti);
            return claimedKeys.add(key); // atomic Set.add mimics Redis SET NX
        }

        public Set<String> getClaimedKeys() {
            return claimedKeys;
        }

        public void setSimulateConnectionFailure(boolean val) {
            this.simulateConnectionFailure = val;
        }

        public void setSimulateTimeout(boolean val) {
            this.simulateTimeout = val;
        }
    }
}
