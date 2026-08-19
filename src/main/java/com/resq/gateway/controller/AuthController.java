package com.resq.gateway.controller;

import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.AuthRequest;
import com.resq.gateway.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @Autowired
    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest request) {
        String username = request.getUsername() != null ? request.getUsername() : "dispatcher@resq.gov";
        String role = request.getRole() != null ? request.getRole().toUpperCase() : "DISPATCHER";
        String fullName = username.split("@")[0].toUpperCase() + " Officer";

        String token = jwtUtil.generateToken(username, role, fullName);
        AuthResponse response = new AuthResponse(token, username, role, fullName, jwtExpirationMs);

        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/demo-tokens")
    public Mono<ResponseEntity<Map<String, Object>>> getDemoTokens() {
        Map<String, Object> tokens = new HashMap<>();

        String adminToken = jwtUtil.generateToken("admin@resq.gov", "ADMIN", "System Administrator");
        String dispatcherToken = jwtUtil.generateToken("dispatcher@resq.gov", "DISPATCHER", "Senior Incident Dispatcher");
        String responderToken = jwtUtil.generateToken("responder@resq.gov", "RESPONDER", "Rescue Team Leader");
        String reporterToken = jwtUtil.generateToken("citizen@resq.gov", "REPORTER", "Public Incident Reporter");

        tokens.put("ADMIN", new AuthResponse(adminToken, "admin@resq.gov", "ADMIN", "System Administrator", jwtExpirationMs));
        tokens.put("DISPATCHER", new AuthResponse(dispatcherToken, "dispatcher@resq.gov", "DISPATCHER", "Senior Incident Dispatcher", jwtExpirationMs));
        tokens.put("RESPONDER", new AuthResponse(responderToken, "responder@resq.gov", "RESPONDER", "Rescue Team Leader", jwtExpirationMs));
        tokens.put("REPORTER", new AuthResponse(reporterToken, "citizen@resq.gov", "REPORTER", "Public Incident Reporter", jwtExpirationMs));

        return Mono.just(ResponseEntity.ok(tokens));
    }
}
