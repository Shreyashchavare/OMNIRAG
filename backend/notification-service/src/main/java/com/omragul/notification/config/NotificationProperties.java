package com.omragul.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Type-safe configuration properties for Notification Service.
 */
@Configuration
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationProperties {

    private JwtProperties jwt = new JwtProperties();
    private PaginationProperties pagination = new PaginationProperties();
    private InternalIngestionProperties internalIngestion = new InternalIngestionProperties();

    @Getter
    @Setter
    public static class JwtProperties {
        private Resource publicKey;
        private String issuer = "http://localhost:8080";
    }

    @Getter
    @Setter
    public static class PaginationProperties {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;
    }

    @Getter
    @Setter
    public static class InternalIngestionProperties {
        private boolean enabled = false;
    }
}
