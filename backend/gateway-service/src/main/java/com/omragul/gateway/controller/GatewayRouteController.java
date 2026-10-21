package com.omragul.gateway.controller;

import com.omragul.gateway.config.GatewayProperties;
import com.omragul.gateway.proxy.ReverseProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Main API Gateway routing controller.
 * Dispatches verified requests to the appropriate backend microservice based on repository route contracts.
 */
@RestController
@RequiredArgsConstructor
public class GatewayRouteController {

    private final ReverseProxyService reverseProxyService;
    private final GatewayProperties gatewayProperties;

    /**
     * Gateway liveness/readiness health endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "gateway-service"
        ));
    }

    /**
     * Routes to Identity Service:
     * - Authentication: /api/v1/auth/** (signup, login, refresh, logout, logout-all)
     * - User management & RBAC: /api/v1/users/**
     */
    @RequestMapping(value = {
            "/api/v1/auth/**",
            "/api/v1/users/**"
    })
    public void routeIdentity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getIdentity().getUrl();
        reverseProxyService.forward("identity-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Routes to Knowledge Service:
     * - Documents: /api/v1/documents/**
     * - Categories: /api/v1/categories/**
     * - Document metadata, permissions, shares, tags, versions
     * - Folders: /api/v1/folders/**, /api/v1/folder-permissions/**
     * - Tags: /api/v1/tags/**
     * - Knowledge bases (canonical downstream path): /knowledge-bases/**
     */
    @RequestMapping(value = {
            "/api/v1/documents/**",
            "/api/v1/categories/**",
            "/api/v1/document-metadata/**",
            "/api/v1/document-permissions/**",
            "/api/v1/document-shares/**",
            "/api/v1/document-tags/**",
            "/api/v1/document-versions/**",
            "/api/v1/folders/**",
            "/api/v1/folder-permissions/**",
            "/api/v1/tags/**",
            "/knowledge-bases/**"
    })
    public void routeKnowledge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getKnowledge().getUrl();
        reverseProxyService.forward("knowledge-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Route knowledge bases when requested under /api/v1/knowledge-bases/**.
     * Rewrites /api/v1/knowledge-bases to /knowledge-bases to match KnowledgeBaseController mapping.
     */
    @RequestMapping(value = "/api/v1/knowledge-bases/**")
    public void routeKnowledgeBasesV1(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getKnowledge().getUrl();
        String rewrittenPath = request.getRequestURI().replaceFirst("^/api/v1/knowledge-bases", "/knowledge-bases");
        reverseProxyService.forward("knowledge-service", targetUrl, rewrittenPath, request, response);
    }

    /**
     * Routes to Processing Service:
     * - Pipeline orchestration & job queries: /api/v1/processing/**
     */
    @RequestMapping(value = "/api/v1/processing/**")
    public void routeProcessing(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getProcessing().getUrl();
        reverseProxyService.forward("processing-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Routes to Analytics Service:
     * - Dashboard, documents, queries, usage, user metrics: /api/analytics/**
     * Note: /internal/analytics/** is not routed through the gateway.
     */
    @RequestMapping(value = "/api/analytics/**")
    public void routeAnalytics(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getAnalytics().getUrl();
        reverseProxyService.forward("analytics-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Routes to Notification Service:
     * - Notification inbox, status, read-marking: /api/notifications/**
     * Note: /internal/notifications/** is not routed through the gateway.
     */
    @RequestMapping(value = "/api/notifications/**")
    public void routeNotification(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getNotification().getUrl();
        reverseProxyService.forward("notification-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Routes to RAG Service:
     * - Vector search, embeddings, indexing: /api/v1/rag/**
     */
    @RequestMapping(value = "/api/v1/rag/**")
    public void routeRag(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getRag().getUrl();
        reverseProxyService.forward("rag-service", targetUrl, request.getRequestURI(), request, response);
    }

    /**
     * Routes to Agent Service:
     * - Chat orchestration & LLM reasoning: /api/v1/agent/**
     */
    @RequestMapping(value = "/api/v1/agent/**")
    public void routeAgent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String targetUrl = gatewayProperties.getServices().getAgent().getUrl();
        reverseProxyService.forward("agent-service", targetUrl, request.getRequestURI(), request, response);
    }
}
