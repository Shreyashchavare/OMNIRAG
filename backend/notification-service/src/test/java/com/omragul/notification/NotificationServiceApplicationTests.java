package com.omragul.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring context smoke test — verifies application starts with test configuration and without internal API key.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the Spring context loads successfully
    }
}
