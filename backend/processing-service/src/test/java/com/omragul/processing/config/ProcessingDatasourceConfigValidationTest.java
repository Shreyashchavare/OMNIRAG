package com.omragul.processing.config;

import com.omragul.processing.ProcessingServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingDatasourceConfigValidationTest {

    @Test
    @DisplayName("Startup fails when mandatory PROCESSING_DB_PASSWORD is missing")
    void testStartupFails_WhenDbPasswordMissing() {
        assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(ProcessingServiceApplication.class)
                    .profiles("default")
                    .properties(
                            "spring.main.web-application-type=none",
                            "spring.flyway.enabled=false",
                            "PROCESSING_DB_URL=jdbc:postgresql://localhost:5432/processing_db",
                            "PROCESSING_DB_USERNAME=postgres"
                            // Intentionally omit PROCESSING_DB_PASSWORD to verify fail-fast
                    )
                    .run();
        });
    }
}
