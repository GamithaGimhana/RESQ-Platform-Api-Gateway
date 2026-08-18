package com.resq.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.ErrorResponse;
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
            "/api/v1/auth/demo-tokens",
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

            // Allow preflight CORS requests
            if (method == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            // Allow open endpoints
            for (String openEndpoint : OPEN_ENDPOINTS) {
                if (path.startsWith(openEndpoint)) {
                    return chain.filter(exchange);
                }
            }

            // Extract Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                // Allow unauthenticated GET for basic incident queries or report creation if configured,
                // but enforce auth on protected operations
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
            try {
                claims = jwtUtil.extractAllClaims(token);
            } catch (Exception e) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Failed to parse JWT Token claims", path);
            }

            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            if (role == null) {
                role = "REPORTER";
            }

            // Role-based Access Control (RBAC) validations
            if (!isAuthorized(path, method, role)) {
                return onError(exchange, HttpStatus.FORBIDDEN, "Insufficient permissions for role: " + role, path);
            }

            // Enrich request with verified identity headers
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", username)
                    .header("X-User-Role", role)
                    .header("X-User-FullName", claims.get("fullName", String.class) != null ? claims.get("fullName", String.class) : username)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private boolean isAuthorized(String path, HttpMethod method, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }

        // DISPATCHER: Full access to incidents, response teams, allocations, evidence viewing
        if ("DISPATCHER".equalsIgnoreCase(role)) {
            return true;
        }

        // RESPONDER: Can view incidents, update incident status, view/manage response, upload evidence
        if ("RESPONDER".equalsIgnoreCase(role)) {
            if (path.startsWith("/api/v1/incidents")) {
                return method == HttpMethod.GET || method == HttpMethod.PATCH || method == HttpMethod.PUT;
            }
            if (path.startsWith("/api/v1/response")) {
                return true;
            }
            if (path.startsWith("/api/v1/evidence")) {
                return true;
            }
            return false;
        }

        // REPORTER: Can create/report incidents and view incident list/details
        if ("REPORTER".equalsIgnoreCase(role)) {
            if (path.startsWith("/api/v1/incidents")) {
                return method == HttpMethod.POST || method == HttpMethod.GET;
            }
            if (path.startsWith("/api/v1/evidence")) {
                return method == HttpMethod.GET || method == HttpMethod.POST;
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
