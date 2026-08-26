package com.resq.gateway.dto;

import java.util.Date;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private String username;
    private String role;
    private String fullName;
    private long expiresInMs;
    private Date issuedAt;
    private UserResponse user;

    public AuthResponse() {
    }

    public AuthResponse(String token, String username, String role, String fullName, long expiresInMs) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.fullName = fullName;
        this.expiresInMs = expiresInMs;
        this.issuedAt = new Date();
    }

    public AuthResponse(String token, UserResponse user, long expiresInMs) {
        this.token = token;
        this.username = user.getEmail();
        this.role = user.getRole().name();
        this.fullName = user.getName();
        this.user = user;
        this.expiresInMs = expiresInMs;
        this.issuedAt = new Date();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
