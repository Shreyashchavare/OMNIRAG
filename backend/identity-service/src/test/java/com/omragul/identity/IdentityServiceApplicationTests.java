package com.omragul.identity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context integration test.
 * Requires a live, healthy PostgreSQL database and is strictly gated by IDENTITY_INTEGRATION_TESTS=true.
 * Ordinary unit and controller test runs will skip this test automatically.
 */
@SpringBootTest
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "IDENTITY_INTEGRATION_TESTS", matches = "true")
class IdentityServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
