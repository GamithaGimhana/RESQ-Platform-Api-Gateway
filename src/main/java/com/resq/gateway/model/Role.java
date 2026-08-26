package com.resq.gateway.model;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    DISPATCHER,
    RESPONDER,
    REPORTER;

    public static Role fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return REPORTER;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean isOperationalStaff() {
        return this == DISPATCHER || this == RESPONDER;
    }
}
