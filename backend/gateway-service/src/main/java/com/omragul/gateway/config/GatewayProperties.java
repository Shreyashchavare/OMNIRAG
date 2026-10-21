package com.omragul.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Services services = new Services();
    private Http http = new Http();
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Services {
        private ServiceEndpoint identity = new ServiceEndpoint("http://identity-service:8080");
        private ServiceEndpoint knowledge = new ServiceEndpoint("http://knowledge-service:8080");
        private ServiceEndpoint processing = new ServiceEndpoint("http://processing-service:8080");
        private ServiceEndpoint analytics = new ServiceEndpoint("http://analytics-service:8080");
        private ServiceEndpoint notification = new ServiceEndpoint("http://notification-service:8080");
        private ServiceEndpoint rag = new ServiceEndpoint("http://rag-service:8000");
        private ServiceEndpoint agent = new ServiceEndpoint("http://agent-service:8000");
    }

    @Getter
    @Setter
    public static class ServiceEndpoint {
        private String url;

        public ServiceEndpoint() {
        }

        public ServiceEndpoint(String url) {
            this.url = url;
        }
    }

    @Getter
    @Setter
    public static class Http {
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 30000;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigin = "http://localhost:3000";
    }

    @Getter
    @Setter
    public static class Jwt {
        private String publicKey = "classpath:certs/public_key.pem";
        private String issuer = "http://localhost:8080";
    }
}
