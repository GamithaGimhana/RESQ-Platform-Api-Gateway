package com.resq.gateway.controller;

import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.*;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive REST controller for User Management endpoints (/api/v1/users).
 * Protected by Spring Cloud Gateway AuthenticationFilter and UserService RBAC rules.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    private Role resolveRole(ServerWebExchange exchange, String roleHeader) {
        if (roleHeader != null && !roleHeader.equalsIgnoreCase("REPORTER") && !roleHeader.isBlank()) {
            return Role.fromString(roleHeader);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                return jwtUtil.extractRole(token);
            }
        }
        return Role.fromString(roleHeader);
    }

    private String resolveUserId(ServerWebExchange exchange, String userIdHeader) {
        if (userIdHeader != null && !userIdHeader.equalsIgnoreCase("system") && !userIdHeader.isBlank()) {
            return userIdHeader;
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                return jwtUtil.extractUserId(token);
            }
        }
        return userIdHeader != null ? userIdHeader : "system";
    }

    @GetMapping
    public Mono<ResponseEntity<List<UserResponse>>> getAllUsers(
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        List<User> users = userService.getAllUsers(role);
        List<UserResponse> responses = users.stream().map(UserResponse::new).collect(Collectors.toList());
        return Mono.just(ResponseEntity.ok(responses));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> getUserById(
            @PathVariable String id,
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        User user = userService.getUserById(id, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(user)));
    }

    @PostMapping
    public Mono<ResponseEntity<UserResponse>> createUser(
            @RequestBody UserCreateRequest request,
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        String actorId = resolveUserId(exchange, actorUserId);
        User created = userService.createUser(request, actorId, role);
        return Mono.just(new ResponseEntity<>(new UserResponse(created), HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> updateUser(
            @PathVariable String id,
            @RequestBody UserUpdateRequest request,
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        String actorId = resolveUserId(exchange, actorUserId);
        User updated = userService.updateUser(id, request, actorId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }

    @PatchMapping("/{id}/role")
    public Mono<ResponseEntity<UserResponse>> updateUserRole(
            @PathVariable String id,
            @RequestBody UserRoleUpdateRequest request,
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        String actorId = resolveUserId(exchange, actorUserId);
        User updated = userService.updateUserRole(id, request.getRole(), actorId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<UserResponse>> updateUserStatus(
            @PathVariable String id,
            @RequestBody UserStatusUpdateRequest request,
            ServerWebExchange exchange,
            @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
            @RequestHeader(value = "X-User-Role", required = false) String roleStr) {
        Role role = resolveRole(exchange, roleStr);
        String actorId = resolveUserId(exchange, actorUserId);
        User updated = userService.updateUserStatus(id, request.getStatus(), actorId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }
}
