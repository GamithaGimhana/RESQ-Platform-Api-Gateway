package com.resq.gateway.dto;

import com.resq.gateway.model.Role;

public class UserRoleUpdateRequest {
    private Role role;

    public UserRoleUpdateRequest() {
    }

    public UserRoleUpdateRequest(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
