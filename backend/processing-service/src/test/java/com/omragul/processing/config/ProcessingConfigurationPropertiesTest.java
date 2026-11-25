package com.omragul.processing.config;

import com.omragul.processing.client.impl.EmbeddingServiceClientImpl;
import com.omragul.processing.client.impl.KnowledgeServiceClientImpl;
import com.omragul.processing.client.impl.MultimodalServiceClientImpl;
import com.omragul.processing.client.impl.RagServiceClientImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    private StandardEnvironment loadApplicationYamlEnvironment(Map<String, Object> overrideProperties) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        if (overrideProperties != null && !overrideProperties.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("explicitOverrides", overrideProperties));
        }
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> propertySources = loader.load("application.yaml", new ClassPathResource("application.yaml"));
        for (PropertySource<?> ps : propertySources) {
            env.getPropertySources().addLast(ps);
        }
        return env;
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found in hierarchy of " + target.getClass());
    }

    @Test
    @DisplayName("PROC-05: Safe defaults resolve from application.yaml when optional environment variables are omitted")
    void testApplicationYamlDefaultPropertyResolution() throws IOException {
        // Validates that Spring property placeholder resolution evaluates safe platform defaults without exceptions.
        // Full application startup is tested separately with the 'test' profile in ProcessingServiceApplicationTests;
        // this test isolates deterministic property source resolution.
        StandardEnvironment env = loadApplicationYamlEnvironment(Map.of());

        // Verify safe orchestration defaults resolve to valid integers
        assertEquals(3, env.getProperty("processing.orchestration.max-attempts", Integer.class),
                "Default max-attempts should resolve to 3");
        assertEquals(1000, env.getProperty("processing.orchestration.connect-timeout-ms", Integer.class),
                "Default connect-timeout-ms should resolve to 1000");
        assertEquals(5000, env.getProperty("processing.orchestration.read-timeout-ms", Integer.class),
                "Default read-timeout-ms should resolve to 5000");
        assertEquals(30000, env.getProperty("processing.orchestration.ai-read-timeout-ms", Integer.class),
                "Default ai-read-timeout-ms should resolve to 30000");
        assertEquals(30000, env.getProperty("processing.multimodal-service.read-timeout-ms", Integer.class),
                "Default multimodal-service read-timeout-ms should resolve to 30000");
        assertEquals(30000, env.getProperty("processing.embedding-service.read-timeout-ms", Integer.class),
                "Default embedding-service read-timeout-ms should resolve to 30000");
        assertEquals(30000, env.getProperty("processing.rag-service.read-timeout-ms", Integer.class),
                "Default rag-service read-timeout-ms should resolve to 30000");

        // Verify safe executor defaults resolve to valid integers
        assertEquals(4, env.getProperty("processing.executor.core-pool-size", Integer.class),
                "Default core-pool-size should resolve to 4");
        assertEquals(8, env.getProperty("processing.executor.max-pool-size", Integer.class),
                "Default max-pool-size should resolve to 8");
        assertEquals(100, env.getProperty("processing.executor.queue-capacity", Integer.class),
                "Default queue-capacity should resolve to 100");

        // Verify downstream service URL defaults
        assertEquals("http://localhost:8082", env.getProperty("processing.knowledge-service.url"),
                "Default knowledge-service URL should resolve to http://localhost:8082");
        assertEquals("http://localhost:8003", env.getProperty("processing.multimodal-service.url"),
                "Default multimodal-service URL should resolve to http://localhost:8003");
        assertEquals("http://localhost:8001", env.getProperty("processing.embedding-service.url"),
                "Default embedding-service URL should resolve to http://localhost:8001");
        assertEquals("http://localhost:8002", env.getProperty("processing.rag-service.url"),
                "Default rag-service URL should resolve to http://localhost:8002");

        // Verify server port default
        assertEquals("8083", env.getProperty("server.port"),
                "Default server.port should resolve to 8083");
    }

    @Test
    @DisplayName("PROC-05: Explicitly supplied environment variables override application.yaml defaults")
    void testApplicationYamlExplicitPropertyOverrides() throws IOException {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("PROCESSING_MAX_ATTEMPTS", "5");
        overrides.put("PROCESSING_CONNECT_TIMEOUT_MS", "2500");
        overrides.put("PROCESSING_READ_TIMEOUT_MS", "15000");
        overrides.put("PROCESSING_EXECUTOR_CORE_POOL_SIZE", "12");
        overrides.put("PROCESSING_EXECUTOR_MAX_POOL_SIZE", "24");
        overrides.put("PROCESSING_EXECUTOR_QUEUE_CAPACITY", "250");
        overrides.put("KNOWLEDGE_SERVICE_URL", "http://custom-knowledge:8082");
        overrides.put("MULTIMODAL_SERVICE_URL", "http://custom-multimodal:8003");
        overrides.put("EMBEDDING_SERVICE_URL", "http://custom-embedding:8001");
        overrides.put("RAG_SERVICE_URL", "http://custom-rag:8002");
        overrides.put("PROCESSING_PORT", "9083");

        StandardEnvironment env = loadApplicationYamlEnvironment(overrides);

        // Verify overridden orchestration properties
        assertEquals(5, env.getProperty("processing.orchestration.max-attempts", Integer.class));
        assertEquals(2500, env.getProperty("processing.orchestration.connect-timeout-ms", Integer.class));
        assertEquals(15000, env.getProperty("processing.orchestration.read-timeout-ms", Integer.class));

        // Verify overridden executor properties
        assertEquals(12, env.getProperty("processing.executor.core-pool-size", Integer.class));
        assertEquals(24, env.getProperty("processing.executor.max-pool-size", Integer.class));
        assertEquals(250, env.getProperty("processing.executor.queue-capacity", Integer.class));

        // Verify overridden downstream service URLs
        assertEquals("http://custom-knowledge:8082", env.getProperty("processing.knowledge-service.url"));
        assertEquals("http://custom-multimodal:8003", env.getProperty("processing.multimodal-service.url"));
        assertEquals("http://custom-embedding:8001", env.getProperty("processing.embedding-service.url"));
        assertEquals("http://custom-rag:8002", env.getProperty("processing.rag-service.url"));

        // Verify overridden server port
        assertEquals("9083", env.getProperty("server.port"));
    }

    @Test
    @DisplayName("PROC-05: AsyncConfig ThreadPoolTaskExecutor property resolution and bean initialization")
    void testAsyncConfigThreadPoolTaskExecutorPropertyResolution() {
        // Test default bean creation with fallback property values
        contextRunner.withUserConfiguration(AsyncConfig.class)
                .run(context -> {
                    assertTrue(context.containsBean("processingTaskExecutor"));
                    ThreadPoolTaskExecutor executor = context.getBean("processingTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertEquals(4, executor.getCorePoolSize());
                    assertEquals(8, executor.getMaxPoolSize());
                    assertEquals(100, executor.getQueueCapacity());
                });

        // Test bean creation with explicit property overrides
        contextRunner.withUserConfiguration(AsyncConfig.class)
                .withPropertyValues(
                        "processing.executor.core-pool-size=16",
                        "processing.executor.max-pool-size=32",
                        "processing.executor.queue-capacity=500"
                )
                .run(context -> {
                    assertTrue(context.containsBean("processingTaskExecutor"));
                    ThreadPoolTaskExecutor executor = context.getBean("processingTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertEquals(16, executor.getCorePoolSize());
                    assertEquals(32, executor.getMaxPoolSize());
                    assertEquals(500, executor.getQueueCapacity());
                });
    }

    @Test
    @DisplayName("PROC-11: Downstream client constructor @Value annotations match platform port and timeout specifications")
    void testClientConstructorValueAnnotationsMatchSpecification() throws NoSuchMethodException {
        // 1. Embedding Service: Port 8001, connect timeout 1000ms, read timeout 5000ms
        Constructor<EmbeddingServiceClientImpl> embConstructor =
                EmbeddingServiceClientImpl.class.getConstructor(String.class, int.class, int.class);
        Value embUrl = embConstructor.getParameters()[0].getAnnotation(Value.class);
        Value embConn = embConstructor.getParameters()[1].getAnnotation(Value.class);
        Value embRead = embConstructor.getParameters()[2].getAnnotation(Value.class);

        assertNotNull(embUrl);
        assertEquals("${processing.embedding-service.url:http://localhost:8001}", embUrl.value());
        assertNotNull(embConn);
        assertEquals("${processing.orchestration.connect-timeout-ms:1000}", embConn.value(),
                "Embedding Service connect timeout default must be 1000ms");
        assertNotNull(embRead);
        assertEquals("${processing.embedding-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}", embRead.value(),
                "Embedding Service read timeout default must be 30000ms");

        // 2. Multimodal Service: Port 8003, connect timeout 1000ms, read timeout 30000ms
        Constructor<MultimodalServiceClientImpl> mmConstructor =
                MultimodalServiceClientImpl.class.getConstructor(String.class, int.class, int.class);
        Value mmUrl = mmConstructor.getParameters()[0].getAnnotation(Value.class);
        Value mmConn = mmConstructor.getParameters()[1].getAnnotation(Value.class);
        Value mmRead = mmConstructor.getParameters()[2].getAnnotation(Value.class);

        assertNotNull(mmUrl);
        assertEquals("${processing.multimodal-service.url:http://localhost:8003}", mmUrl.value());
        assertNotNull(mmConn);
        assertEquals("${processing.orchestration.connect-timeout-ms:1000}", mmConn.value(),
                "Multimodal Service connect timeout default must be 1000ms");
        assertNotNull(mmRead);
        assertEquals("${processing.multimodal-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}", mmRead.value(),
                "Multimodal Service read timeout default must be 30000ms");

        // 3. RAG Service: Port 8002, connect timeout 1000ms, read timeout 30000ms
        Constructor<RagServiceClientImpl> ragConstructor =
                RagServiceClientImpl.class.getConstructor(String.class, int.class, int.class);
        Value ragUrl = ragConstructor.getParameters()[0].getAnnotation(Value.class);
        Value ragConn = ragConstructor.getParameters()[1].getAnnotation(Value.class);
        Value ragRead = ragConstructor.getParameters()[2].getAnnotation(Value.class);

        assertNotNull(ragUrl);
        assertEquals("${processing.rag-service.url:http://localhost:8002}", ragUrl.value());
        assertNotNull(ragConn);
        assertEquals("${processing.orchestration.connect-timeout-ms:1000}", ragConn.value(),
                "RAG Service connect timeout default must be 1000ms");
        assertNotNull(ragRead);
        assertEquals("${processing.rag-service.read-timeout-ms:${processing.orchestration.ai-read-timeout-ms:30000}}", ragRead.value(),
                "RAG Service read timeout default must be 30000ms");

        // 4. Knowledge Service: Port 8082, connect timeout 1000ms, read timeout 5000ms
        Constructor<KnowledgeServiceClientImpl> ksConstructor =
                KnowledgeServiceClientImpl.class.getConstructor(String.class, String.class, int.class, int.class);
        Value ksUrl = ksConstructor.getParameters()[0].getAnnotation(Value.class);
        Value ksConn = ksConstructor.getParameters()[2].getAnnotation(Value.class);
        Value ksRead = ksConstructor.getParameters()[3].getAnnotation(Value.class);

        assertNotNull(ksUrl);
        assertEquals("${processing.knowledge-service.url:http://localhost:8082}", ksUrl.value());
        assertNotNull(ksConn);
        assertEquals("${processing.orchestration.connect-timeout-ms:1000}", ksConn.value(),
                "Knowledge Service connect timeout default must be 1000ms");
        assertNotNull(ksRead);
        assertEquals("${processing.orchestration.read-timeout-ms:5000}", ksRead.value(),
                "Knowledge Service read timeout default must be 5000ms");
    }

    @Test
    @DisplayName("PROC-11: Injected HTTP timeout values are genuinely applied to underlying request factories")
    void testHttpRequestFactoryTimeoutsApplied() throws Exception {
        int customConnectTimeout = 1250;
        int customReadTimeout = 4750;

        // 1. EmbeddingServiceClientImpl -> SimpleClientHttpRequestFactory
        EmbeddingServiceClientImpl embeddingClient = new EmbeddingServiceClientImpl(
                "http://localhost:8001", customConnectTimeout, customReadTimeout);
        RestClient embRestClient = (RestClient) getPrivateField(embeddingClient, "restClient");
        ClientHttpRequestFactory embFactory = (ClientHttpRequestFactory) getPrivateField(embRestClient, "clientRequestFactory");
        assertInstanceOf(SimpleClientHttpRequestFactory.class, embFactory);
        assertEquals(customConnectTimeout, getPrivateField(embFactory, "connectTimeout"),
                "Embedding client connectTimeout must be set on SimpleClientHttpRequestFactory");
        assertEquals(customReadTimeout, getPrivateField(embFactory, "readTimeout"),
                "Embedding client readTimeout must be set on SimpleClientHttpRequestFactory");

        // 2. MultimodalServiceClientImpl -> SimpleClientHttpRequestFactory
        MultimodalServiceClientImpl multimodalClient = new MultimodalServiceClientImpl(
                "http://localhost:8003", customConnectTimeout, customReadTimeout);
        RestClient mmRestClient = (RestClient) getPrivateField(multimodalClient, "restClient");
        ClientHttpRequestFactory mmFactory = (ClientHttpRequestFactory) getPrivateField(mmRestClient, "clientRequestFactory");
        assertInstanceOf(SimpleClientHttpRequestFactory.class, mmFactory);
        assertEquals(customConnectTimeout, getPrivateField(mmFactory, "connectTimeout"),
                "Multimodal client connectTimeout must be set on SimpleClientHttpRequestFactory");
        assertEquals(customReadTimeout, getPrivateField(mmFactory, "readTimeout"),
                "Multimodal client readTimeout must be set on SimpleClientHttpRequestFactory");

        // 3. RagServiceClientImpl -> SimpleClientHttpRequestFactory
        RagServiceClientImpl ragClient = new RagServiceClientImpl(
                "http://localhost:8002", customConnectTimeout, customReadTimeout);
        RestClient ragRestClient = (RestClient) getPrivateField(ragClient, "restClient");
        ClientHttpRequestFactory ragFactory = (ClientHttpRequestFactory) getPrivateField(ragRestClient, "clientRequestFactory");
        assertInstanceOf(SimpleClientHttpRequestFactory.class, ragFactory);
        assertEquals(customConnectTimeout, getPrivateField(ragFactory, "connectTimeout"),
                "RAG client connectTimeout must be set on SimpleClientHttpRequestFactory");
        assertEquals(customReadTimeout, getPrivateField(ragFactory, "readTimeout"),
                "RAG client readTimeout must be set on SimpleClientHttpRequestFactory");

        // 4. KnowledgeServiceClientImpl -> JdkClientHttpRequestFactory
        KnowledgeServiceClientImpl knowledgeClient = new KnowledgeServiceClientImpl(
                "http://localhost:8082", "test-secret-12345", customConnectTimeout, customReadTimeout);
        RestClient ksRestClient = (RestClient) getPrivateField(knowledgeClient, "restClient");
        ClientHttpRequestFactory ksFactory = (ClientHttpRequestFactory) getPrivateField(ksRestClient, "clientRequestFactory");
        assertInstanceOf(JdkClientHttpRequestFactory.class, ksFactory);
        HttpClient httpClient = (HttpClient) getPrivateField(ksFactory, "httpClient");
        assertEquals(Duration.ofMillis(customConnectTimeout), httpClient.connectTimeout().orElse(null),
                "Knowledge client connectTimeout must be set on underlying HttpClient");
        assertEquals(Duration.ofMillis(customReadTimeout), getPrivateField(ksFactory, "readTimeout"),
                "Knowledge client readTimeout must be set on JdkClientHttpRequestFactory");
    }

    @Test
    @DisplayName("PROC-14 & PROC-11: Processing Service .env file syntax is valid dotenv format and ports match specification")
    void testDotEnvFileSyntaxAndPortConventions() throws IOException {
        // Verifies static syntax and default port definitions in backend/processing-service/.env.
        // Note: This verifies the template file syntax and does not claim to test Spring Boot dotenv runtime loading.
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            envPath = Path.of("backend/processing-service/.env");
        }
        assertTrue(Files.exists(envPath), "Processing Service .env file must exist at " + envPath.toAbsolutePath());

        List<String> lines = Files.readAllLines(envPath);
        assertFalse(lines.isEmpty(), ".env file must not be empty");

        // PROC-14: Line 1 must not be a bare un-commented delimiter
        String line1 = lines.get(0).trim();
        assertFalse(line1.startsWith("---"), "Line 1 must not be a bare delimiter without comment marker '#': " + line1);
        assertTrue(line1.startsWith("#") || line1.contains("="), "Line 1 must be a comment or variable assignment: " + line1);

        // Every non-blank line must be a valid comment or KEY=VALUE assignment
        Properties props = new Properties();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            assertTrue(line.contains("="), "Line " + (i + 1) + " must be a comment or valid KEY=VALUE pair: " + line);
            int eqIdx = line.indexOf('=');
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            assertFalse(key.isEmpty(), "Line " + (i + 1) + " key must not be empty: " + line);
            props.setProperty(key, value);
        }

        // PROC-11: Port assignments in .env must match the platform specification
        assertEquals("8001", props.getProperty("EMBEDDING_PORT"), "EMBEDDING_PORT must be 8001");
        assertEquals("8002", props.getProperty("RAG_PORT"), "RAG_PORT must be 8002");
        assertEquals("8003", props.getProperty("MULTIMODAL_PORT"), "MULTIMODAL_PORT must be 8003");
        assertEquals("8004", props.getProperty("AGENT_PORT"), "AGENT_PORT must be 8004");
        assertEquals("8083", props.getProperty("PROCESSING_PORT"), "PROCESSING_PORT must be 8083");

        // Downstream URLs in .env
        assertEquals("http://localhost:8001", props.getProperty("EMBEDDING_SERVICE_URL"),
                "EMBEDDING_SERVICE_URL must point to port 8001");
        assertEquals("http://localhost:8003", props.getProperty("MULTIMODAL_SERVICE_URL"),
                "MULTIMODAL_SERVICE_URL must point to port 8003");
        assertEquals("http://localhost:8002", props.getProperty("RAG_SERVICE_URL"),
                "RAG_SERVICE_URL must point to port 8002");
        assertEquals("http://localhost:8082", props.getProperty("KNOWLEDGE_SERVICE_URL"),
                "KNOWLEDGE_SERVICE_URL must point to port 8082");
    }
}
