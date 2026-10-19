package com.omragul.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Analytics service configuration properties.
 * Values are read from environment variables via application.yaml.
 */
@Configuration
public class AnalyticsProperties {

    @Value("${analytics.pagination.default-page-size:20}")
    private int defaultPageSize;

    @Value("${analytics.pagination.max-page-size:100}")
    private int maxPageSize;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }
}
