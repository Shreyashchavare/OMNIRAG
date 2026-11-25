package com.omragul.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSecurityFilterTest {

    private static final String TEST_SECRET = "test-internal-service-secret-54321";
    private static KeyPair testKeyPair;
    private static KeyPair rogueKeyPair;

    private SecurityContextFilter filter;
    private InternalAuthService internalAuthService;
    private GatewayAssertionVerifier gatewayAssertionVerifier;
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
        gatewayAssertionVerifier = new GatewayAssertionVerifier(testKeyPair.getPublic(), objectMapper);
        securityAuditLogger = new SecurityAuditLogger();
        filter = new SecurityContextFilter(internalAuthService, gatewayAssertionVerifier, securityAuditLogger, objectMapper);
    }

    @AfterEach
    void tearDown() {
        UserSecurityContextHolder.clear();
    }

    private String createAssertion(String userId, String roles, String requestId, KeyPair keyPair, long expiryDelta) {
        return createAssertion(userId, roles, requestId, "POST", "/api/v1/documents", keyPair, expiryDelta);
    }

    private String createAssertion(String userId, String roles, String requestId, String method, String path, KeyPair keyPair, long expiryDelta) {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer("gateway-service")
                .audience().single("knowledge-service")
                .subject(userId)
                .claim("roles", roles != null ? roles : "")
                .claim("rid", requestId != null ? requestId : "req-default")
                .claim("iat", now)
                .claim("exp", now + expiryDelta)
                .claim("mth", method != null ? method : "POST")
                .claim("pth", path != null ? path : "/api/v1/documents")
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("1. Missing credentials returns 401 Unauthorized")
    void testMissingCredentials_Returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Authentication required: missing user identity or internal service credentials"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("2. Direct untrusted X-User-Id header without assertion returns 401 Unauthorized")
    void testDirectUntrustedUserId_Returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Direct client identity headers rejected: gateway assertion required"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("3. Tampered assertion signature returns 401 Unauthorized")
    void testTamperedAssertionSignature_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String tamperedToken = createAssertion(userId.toString(), "ROLE_USER", "req-1", rogueKeyPair, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", tamperedToken);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid or expired gateway assertion"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("3b. Legacy HMAC assertion returns 401 Unauthorized")
    void testLegacyHmacAssertion_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String legacyToken = "eyJzdWIiOiIxMjM0In0.fakehmacsig1234567890abcdef1234567890abcdef1234567890abcdef12345678";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", legacyToken);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Legacy HMAC gateway assertions are rejected; RS256 assertions required"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("4. Identity Spoofing Attempt: Assertion user ID does not match X-User-Id header returns 401")
    void testIdentitySpoofingMismatch_Returns401() throws Exception {
        UUID realUser = UUID.randomUUID();
        UUID victimUser = UUID.randomUUID();
        String token = createAssertion(realUser.toString(), "ROLE_USER", "req-1", testKeyPair, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", token);
        request.addHeader("X-User-Id", victimUser.toString()); // Spoofed victim header!
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Gateway assertion user ID does not match X-User-Id header"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("5. Expired assertion returns 401 Unauthorized")
    void testExpiredAssertion_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String expiredToken = createAssertion(userId.toString(), "ROLE_USER", "req-1", "GET", "/api/v1/documents", testKeyPair, -60);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", expiredToken);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Assertion expired") || response.getContentAsString().contains("expired"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("6. Valid Gateway assertion succeeds and sets UserSecurityContext")
    void testValidAssertion_Succeeds() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = createAssertion(userId.toString(), "ROLE_USER,ROLE_ADMIN", "req-1", testKeyPair, 60);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", validToken);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] filterExecuted = {false};
        FilterChain capturingChain = (req, res) -> {
            filterExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context);
            assertEquals(userId, context.getUserId());
            assertTrue(context.isAdmin());
            assertFalse(context.isInternalService());
            // Verify request wrapper provides verified user ID downstream
            assertEquals(userId.toString(), ((HttpServletRequest) req).getHeader("X-User-Id"));
        };

        filter.doFilter(request, response, capturingChain);

        assertTrue(filterExecuted[0]);
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext(), "Context must be cleared after filter completion");
    }

    @Test
    @DisplayName("7. Valid internal secret on authorized internal endpoint succeeds as internal service")
    void testValidInternalSecret_AuthorizedInternalEndpoint_Succeeds() throws Exception {
        UUID docId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents/" + docId);
        request.addHeader("X-Internal-Secret", TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] filterExecuted = {false};
        FilterChain capturingChain = (req, res) -> {
            filterExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context);
            assertTrue(context.isInternalService());
            assertNull(context.getUserId());
        };

        filter.doFilter(request, response, capturingChain);

        assertTrue(filterExecuted[0]);
        assertEquals(200, response.getStatus());
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("8. Valid internal secret on batch/status internal endpoint succeeds")
    void testValidInternalSecret_BatchStatusEndpoint_Succeeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/batch/status");
        request.addHeader("X-Internal-Service-Secret", TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] filterExecuted = {false};
        FilterChain capturingChain = (req, res) -> {
            filterExecuted[0] = true;
            UserSecurityContext context = UserSecurityContextHolder.getContext();
            assertNotNull(context);
            assertTrue(context.isInternalService());
        };

        filter.doFilter(request, response, capturingChain);

        assertTrue(filterExecuted[0]);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("9. Invalid internal secret returns 401 Unauthorized")
    void testInvalidInternalSecret_Returns401() throws Exception {
        UUID docId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents/" + docId);
        request.addHeader("X-Internal-Secret", "wrong-secret-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid internal service credentials"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("10. Requirement 6: Internal secret on user-facing endpoint returns 403 Forbidden")
    void testInternalSecret_UserFacingEndpoint_Returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents");
        request.addHeader("X-Internal-Secret", TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Internal service credentials not authorized for user-facing endpoint"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("11. Requirement 6: Internal secret on knowledge-bases user endpoint returns 403 Forbidden")
    void testInternalSecret_KnowledgeBasesEndpoint_Returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/knowledge-bases");
        request.addHeader("X-Internal-Secret", TEST_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Internal service credentials not authorized for user-facing endpoint"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("12. Actuator and Swagger documentation endpoints bypass filter")
    void testActuatorAndDocs_BypassFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] filterExecuted = {false};
        FilterChain filterChain = (req, res) -> filterExecuted[0] = true;

        filter.doFilter(request, response, filterChain);

        assertTrue(filterExecuted[0]);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("13. Method substitution attack returns 401 Unauthorized")
    void testMethodSubstitution_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        // Token issued for GET /api/v1/documents
        String token = createAssertion(userId.toString(), "ROLE_USER", "req-1", "GET", "/api/v1/documents", testKeyPair, 60);

        // Attacker replays with DELETE /api/v1/documents
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/documents");
        request.addHeader("X-Gateway-Assertion", token);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid gateway assertion method"));
        assertNull(UserSecurityContextHolder.getContext());
    }

    @Test
    @DisplayName("14. Path substitution attack returns 401 Unauthorized")
    void testPathSubstitution_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        // Token issued for POST /api/v1/documents
        String token = createAssertion(userId.toString(), "ROLE_USER", "req-1", "POST", "/api/v1/documents", testKeyPair, 60);

        // Attacker replays to different path /api/v1/documents/secret
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/secret");
        request.addHeader("X-Gateway-Assertion", token);
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid gateway assertion path"));
        assertNull(UserSecurityContextHolder.getContext());
    }
}
