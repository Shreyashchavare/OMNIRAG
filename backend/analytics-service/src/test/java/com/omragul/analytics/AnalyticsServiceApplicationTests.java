package com.omragul.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring context smoke test — verifies the application starts with Testcontainers PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

	@Test
	void contextLoads() {
		// Passes if the Spring context loads without errors
	}

}
