package com.resq.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> info = new HashMap<>();
        info.put("platform", "RESQ Emergency Dispatch & Incident Management System");
        info.put("component", "API Gateway (Cloud Load Balancer Endpoint)");
        info.put("status", "UP");
        info.put("environment", "GCP Multi-Zone Production");
        info.put("project", "resq-enterprise-cloud-01");
        info.put("health", "/actuator/health");
        
        Map<String, String> routes = new HashMap<>();
        routes.put("incidents", "/api/v1/incidents");
        routes.put("response_teams", "/api/v1/response/teams");
        routes.put("evidence_vault", "/api/v1/evidence");
        routes.put("authentication", "/api/v1/auth");
        routes.put("frontend_ui", "http://136.110.162.65");
        routes.put("eureka_discovery_node1", "http://8.231.85.125:8761");
        routes.put("eureka_discovery_node2", "http://8.234.116.15:8761");
        info.put("routes", routes);

        return ResponseEntity.ok(info);
    }
}
