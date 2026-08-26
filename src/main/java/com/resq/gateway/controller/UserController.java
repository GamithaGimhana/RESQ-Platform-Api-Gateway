package com.resq.gateway.controller;

import com.resq.gateway.dto.UserCreateRequest;
import com.resq.gateway.dto.UserResponse;
import com.resq.gateway.dto.UserRoleUpdateRequest;
import com.resq.gateway.dto.UserStatusUpdateRequest;
import com.resq.gateway.dto.UserUpdateRequest;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<UserResponse>>> getAllUsers(
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        List<User> users = userService.getAllUsers(role);
        List<UserResponse> responses = users.stream().map(UserResponse::new).collect(Collectors.toList());
        return Mono.just(ResponseEntity.ok(responses));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> getUserById(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        User user = userService.getUserById(id, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(user)));
    }

    @PostMapping
    public Mono<ResponseEntity<UserResponse>> createUser(
            @RequestBody UserCreateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        User created = userService.createUser(request, actorUserId, role);
        return Mono.just(new ResponseEntity<>(new UserResponse(created), HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> updateUser(
            @PathVariable String id,
            @RequestBody UserUpdateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        User updated = userService.updateUser(id, request, actorUserId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }

    @PatchMapping("/{id}/role")
    public Mono<ResponseEntity<UserResponse>> updateUserRole(
            @PathVariable String id,
            @RequestBody UserRoleUpdateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        User updated = userService.updateUserRole(id, request.getRole(), actorUserId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<UserResponse>> updateUserStatus(
            @PathVariable String id,
            @RequestBody UserStatusUpdateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actorUserId,
            @RequestHeader(value = "X-User-Role", defaultValue = "REPORTER") String roleStr) {
        Role role = Role.fromString(roleStr);
        User updated = userService.updateUserStatus(id, request.getStatus(), actorUserId, role);
        return Mono.just(ResponseEntity.ok(new UserResponse(updated)));
    }
}
