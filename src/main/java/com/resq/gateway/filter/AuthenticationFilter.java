package com.resq.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.ErrorResponse;
import com.resq.gateway.model.Role;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> OPEN_ENDPOINTS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/demo-tokens",
            "/api/v1/evidence/local",
            "/api/v1/evidence/file",
            "/api/v1/evidence/download",
            "/actuator/health",
            "/actuator/info"
    );

    @Autowired
    public AuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            HttpMethod method = request.getMethod();

            // 1. Allow preflight CORS requests
            if (method == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            // 2. Allow open endpoints
            if ("/".equals(path) || path.isEmpty()) {
                return chain.filter(exchange);
            }
            for (String openEndpoint : OPEN_ENDPOINTS) {
                if (path.startsWith(openEndpoint)) {
                    // Strip any spoofed headers even on open endpoints
                    ServerHttpRequest sanitizedRequest = request.mutate()
                            .headers(h -> {
                                h.remove("X-User-Id");
                                h.remove("X-User-Role");
                                h.remove("X-User-FullName");
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(sanitizedRequest).build());
                }
            }

            // 3. Header Spoofing Protection: Verify and extract Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization Header", path);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid Authorization Header Format", path);
            }

            String token = authHeader.substring(7).trim();
            if (!jwtUtil.validateToken(token)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or Expired JWT Token", path);
            }

            Claims claims;
            Role role;
            String username;
            String fullName;
            try {
                claims = jwtUtil.extractAllClaims(token);
                role = jwtUtil.extractRole(token);
                username = jwtUtil.extractUserId(token);
                fullName = claims.get("fullName", String.class) != null ? claims.get("fullName", String.class) : username;
            } catch (Exception e) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Failed to parse JWT Token claims: " + e.getMessage(), path);
            }

            // 4. Role-based Access Control (RBAC) validations
            if (!isAuthorized(path, method, role)) {
                log.warn("Access Denied: User [{}] with Role [{}] attempted unauthorized [{}] on [{}]", username, role, method, path);
                return onError(exchange, HttpStatus.FORBIDDEN, "Insufficient permissions for role: " + role, path);
            }

            // 5. Header Spoofing Protection: Strip any client headers, inject verified identity headers
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-User-Id");
                        httpHeaders.remove("X-User-Role");
                        httpHeaders.remove("X-User-FullName");
                    })
                    .header("X-User-Id", username)
                    .header("X-User-Role", role.name())
                    .header("X-User-FullName", fullName)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public boolean isAuthorized(String path, HttpMethod method, Role role) {
        if (role == null) {
            return false;
        }

        // SUPER_ADMIN: Full access across entire platform
        if (role == Role.SUPER_ADMIN) {
            return true;
        }

        // ADMIN: Operational administrator
        if (role == Role.ADMIN) {
            // ADMIN has full access to incidents, response, evidence, operational users, audit
            return true;
        }

        // DISPATCHER: Emergency dispatch & resource coordination
        if (role == Role.DISPATCHER) {
            // User management: NO access
            if (path.startsWith("/api/v1/users")) {
                return false;
            }

            // Incidents
            if (path.startsWith("/api/v1/incidents")) {
                // Cannot overwrite incident metadata via PUT
                if (method == HttpMethod.PUT) {
                    return false;
                }
                // Status operational containment is for responders/admins
                if (path.matches("^/api/v1/incidents/\\d+/status$")) {
                    return false;
                }
                // Allowed: GET (all), POST /incidents (create), POST /incidents/{id}/assignments (assign squad)
                return true;
            }

            // Response teams & resources
            if (path.startsWith("/api/v1/response")) {
                return true; // Dispatcher manages teams, resources, allocations
            }

            // Evidence
            if (path.startsWith("/api/v1/evidence")) {
                // Dispatcher cannot delete evidence or access audit events
                if (path.startsWith("/api/v1/evidence/audit") || method == HttpMethod.DELETE || method == HttpMethod.POST) {
                    return false;
                }
                return method == HttpMethod.GET;
            }

            return false;
        }

        // RESPONDER: Field rescue execution
        if (role == Role.RESPONDER) {
            // User management: NO access
            if (path.startsWith("/api/v1/users")) {
                return false;
            }

            // Incidents
            if (path.startsWith("/api/v1/incidents")) {
                // Cannot create incidents, assign squads, or PUT incident
                if (path.matches("^/api/v1/incidents/\\d+/assignments$") || method == HttpMethod.PUT) {
                    return false;
                }
                // Allowed: GET (view incidents) and PATCH /incidents/{id}/status (update containment status)
                return method == HttpMethod.GET || (method == HttpMethod.PATCH && path.matches("^/api/v1/incidents/\\d+/status$"));
            }

            // Response
            if (path.startsWith("/api/v1/response")) {
                // Read-only access to view teams, resources, allocations
                return method == HttpMethod.GET;
            }

            // Evidence
            if (path.startsWith("/api/v1/evidence")) {
                if (path.startsWith("/api/v1/evidence/audit") || method == HttpMethod.DELETE) {
                    return false;
                }
                // Can upload evidence and view evidence
                return method == HttpMethod.GET || (method == HttpMethod.POST && path.startsWith("/api/v1/evidence/upload"));
            }

            return false;
        }

        // REPORTER: Public disaster reporting & tracking
        if (role == Role.REPORTER) {
            // User management: NO access
            if (path.startsWith("/api/v1/users")) {
                return false;
            }

            // Incidents
            if (path.startsWith("/api/v1/incidents")) {
                // Cannot assign teams, modify status, or PUT incident
                if (path.matches("^/api/v1/incidents/\\d+/assignments$") ||
                        path.matches("^/api/v1/incidents/\\d+/status$") ||
                        method == HttpMethod.PUT || method == HttpMethod.PATCH || method == HttpMethod.DELETE) {
                    return false;
                }
                // Can create incident (POST) or view incidents (GET)
                return method == HttpMethod.POST || method == HttpMethod.GET;
            }

            // Response teams/resources: NO access
            if (path.startsWith("/api/v1/response")) {
                return false;
            }

            // Evidence: Can upload disaster evidence and view incident media
            if (path.startsWith("/api/v1/evidence")) {
                if (path.startsWith("/api/v1/evidence/audit") || method == HttpMethod.DELETE) {
                    return false;
                }
                return method == HttpMethod.GET || (method == HttpMethod.POST && path.startsWith("/api/v1/evidence/upload"));
            }

            return false;
        }

        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message, String path) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        if (traceId == null) {
            traceId = "resq-auth-err";
        }

        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                traceId
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
