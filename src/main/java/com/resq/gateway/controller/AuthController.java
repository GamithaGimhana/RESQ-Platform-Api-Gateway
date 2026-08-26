package com.resq.gateway.controller;

import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.AuthRequest;
import com.resq.gateway.dto.AuthResponse;
import com.resq.gateway.dto.RegisterRequest;
import com.resq.gateway.dto.UserResponse;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final UserService userService;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @Autowired
    public AuthController(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@RequestBody RegisterRequest request) {
        // Public registration always assigns Role.REPORTER
        User registeredUser = userService.registerPublicReporter(request);
        String token = jwtUtil.generateToken(registeredUser.getId(), registeredUser.getEmail(), registeredUser.getRole().name(), registeredUser.getName());
        AuthResponse response = new AuthResponse(token, new UserResponse(registeredUser), jwtExpirationMs);
        return Mono.just(new ResponseEntity<>(response, HttpStatus.CREATED));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody AuthRequest request) {
        String identifier = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        // Verify credentials against user store and get true verified role
        User user = userService.authenticate(identifier, password);
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), user.getName());
        AuthResponse response = new AuthResponse(token, new UserResponse(user), jwtExpirationMs);

        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/demo-tokens")
    public Mono<ResponseEntity<Map<String, Object>>> getDemoTokens() {
        Map<String, Object> tokens = new HashMap<>();

        String superAdminToken = jwtUtil.generateToken("usr-superadmin", "superadmin@resq.gov", Role.SUPER_ADMIN.name(), "Super Administrator");
        String adminToken = jwtUtil.generateToken("usr-admin", "admin@resq.gov", Role.ADMIN.name(), "Lead Cloud Architect");
        String dispatcherToken = jwtUtil.generateToken("usr-dispatcher", "dispatcher@resq.gov", Role.DISPATCHER.name(), "Senior Incident Dispatcher");
        String responderToken = jwtUtil.generateToken("usr-responder", "responder@resq.gov", Role.RESPONDER.name(), "Rescue Team Leader");
        String reporterToken = jwtUtil.generateToken("usr-reporter", "citizen@resq.gov", Role.REPORTER.name(), "Public Incident Reporter");

        tokens.put("SUPER_ADMIN", new AuthResponse(superAdminToken, "superadmin@resq.gov", Role.SUPER_ADMIN.name(), "Super Administrator", jwtExpirationMs));
        tokens.put("ADMIN", new AuthResponse(adminToken, "admin@resq.gov", Role.ADMIN.name(), "Lead Cloud Architect", jwtExpirationMs));
        tokens.put("DISPATCHER", new AuthResponse(dispatcherToken, "dispatcher@resq.gov", Role.DISPATCHER.name(), "Senior Incident Dispatcher", jwtExpirationMs));
        tokens.put("RESPONDER", new AuthResponse(responderToken, "responder@resq.gov", Role.RESPONDER.name(), "Rescue Team Leader", jwtExpirationMs));
        tokens.put("REPORTER", new AuthResponse(reporterToken, "citizen@resq.gov", Role.REPORTER.name(), "Public Incident Reporter", jwtExpirationMs));

        return Mono.just(ResponseEntity.ok(tokens));
    }
}
