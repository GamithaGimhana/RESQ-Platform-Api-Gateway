package com.resq.gateway.security;

import com.resq.gateway.config.JwtUtil;
import com.resq.gateway.dto.RegisterRequest;
import com.resq.gateway.dto.UserCreateRequest;
import com.resq.gateway.filter.AuthenticationFilter;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.model.UserStatus;
import com.resq.gateway.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false"
})
public class ApiGatewaySecurityTests {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationFilter authenticationFilter;

    @Nested
    @DisplayName("1. JWT Authentication & Validation Tests")
    class JwtTests {

        @Test
        @DisplayName("Valid JWT generation and extraction for all 5 roles")
        void testTokenGenerationForAllRoles() {
            for (Role role : Role.values()) {
                String token = jwtUtil.generateToken("user-" + role.name(), role.name().toLowerCase() + "@resq.gov", role.name(), "Test User");
                assertNotNull(token);
                assertTrue(jwtUtil.validateToken(token));
                assertEquals(role, jwtUtil.extractRole(token));
            }
        }

        @Test
        @DisplayName("Invalid, malformed, and empty JWT tokens rejected")
        void testInvalidTokensRejected() {
            assertFalse(jwtUtil.validateToken(null));
            assertFalse(jwtUtil.validateToken(""));
            assertFalse(jwtUtil.validateToken("invalid.jwt.token"));
            assertFalse(jwtUtil.validateToken("Bearer dummy-token"));
        }
    }

    @Nested
    @DisplayName("2. Public Registration & Privilege Escalation Protection")
    class UserManagementSecurityTests {

        @Test
        @DisplayName("Public registration strictly creates REPORTER role only")
        void testPublicRegistrationAlwaysReporter() {
            String uniqueEmail = "reporter_" + UUID.randomUUID().toString().substring(0, 6) + "@public.lk";
            RegisterRequest req = new RegisterRequest("Citizen Jane", uniqueEmail, "+94771234567", "Password123!");
            User registered = userService.registerPublicReporter(req);

            assertNotNull(registered);
            assertEquals(Role.REPORTER, registered.getRole());
            assertEquals(UserStatus.ACTIVE, registered.getStatus());
        }

        @Test
        @DisplayName("ADMIN cannot create SUPER_ADMIN or ADMIN (Privilege Escalation Blocked)")
        void testAdminCannotCreatePrivilegedRoles() {
            UserCreateRequest superAdminReq = new UserCreateRequest("New Super Admin", "hack1@resq.gov", "+94770000000", "Pass123!", Role.SUPER_ADMIN);
            assertThrows(SecurityException.class, () -> userService.createUser(superAdminReq, "usr-admin", Role.ADMIN));

            UserCreateRequest adminReq = new UserCreateRequest("New Admin", "hack2@resq.gov", "+94770000000", "Pass123!", Role.ADMIN);
            assertThrows(SecurityException.class, () -> userService.createUser(adminReq, "usr-admin", Role.ADMIN));
        }

        @Test
        @DisplayName("ADMIN can create operational roles (DISPATCHER, RESPONDER, REPORTER)")
        void testAdminCanCreateOperationalRoles() {
            String email = "disp_" + UUID.randomUUID().toString().substring(0, 6) + "@resq.gov";
            UserCreateRequest req = new UserCreateRequest("Field Dispatcher", email, "+94770000000", "Pass123!", Role.DISPATCHER);
            User created = userService.createUser(req, "usr-admin", Role.ADMIN);
            assertNotNull(created);
            assertEquals(Role.DISPATCHER, created.getRole());
        }

        @Test
        @DisplayName("SUPER_ADMIN can create any role including ADMIN and SUPER_ADMIN")
        void testSuperAdminCanCreateAnyRole() {
            String email = "admin_" + UUID.randomUUID().toString().substring(0, 6) + "@resq.gov";
            UserCreateRequest req = new UserCreateRequest("New Admin", email, "+94770000000", "Pass123!", Role.ADMIN);
            User created = userService.createUser(req, "usr-superadmin", Role.SUPER_ADMIN);
            assertNotNull(created);
            assertEquals(Role.ADMIN, created.getRole());
        }

        @Test
        @DisplayName("User cannot elevate own security role")
        void testUserCannotElevateOwnRole() {
            User admin = userService.findByEmail("admin@resq.gov");
            assertNotNull(admin);
            assertThrows(SecurityException.class, () -> userService.updateUserRole(admin.getId(), Role.SUPER_ADMIN, admin.getId(), Role.ADMIN));
        }
    }

    @Nested
    @DisplayName("3. Gateway Granular RBAC Matrix Route Tests")
    class GatewayRbacMatrixTests {

        @Test
        @DisplayName("SUPER_ADMIN has universal access")
        void testSuperAdminAccess() {
            assertTrue(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.GET, Role.SUPER_ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.POST, Role.SUPER_ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.POST, Role.SUPER_ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.POST, Role.SUPER_ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/evidence/audit", HttpMethod.GET, Role.SUPER_ADMIN));
        }

        @Test
        @DisplayName("ADMIN operational access matrix")
        void testAdminAccess() {
            assertTrue(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.GET, Role.ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.POST, Role.ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.POST, Role.ADMIN));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/evidence/audit", HttpMethod.GET, Role.ADMIN));
        }

        @Test
        @DisplayName("DISPATCHER permissions vs restrictions")
        void testDispatcherAccess() {
            // Allowed
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.GET, Role.DISPATCHER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.POST, Role.DISPATCHER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents/1/assignments", HttpMethod.POST, Role.DISPATCHER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.POST, Role.DISPATCHER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/response/allocations", HttpMethod.POST, Role.DISPATCHER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/evidence/1", HttpMethod.GET, Role.DISPATCHER));

            // Forbidden (403)
            assertFalse(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.GET, Role.DISPATCHER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/incidents/1", HttpMethod.PUT, Role.DISPATCHER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/incidents/1/status", HttpMethod.PATCH, Role.DISPATCHER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/evidence/audit", HttpMethod.GET, Role.DISPATCHER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/evidence/1", HttpMethod.DELETE, Role.DISPATCHER));
        }

        @Test
        @DisplayName("RESPONDER permissions vs restrictions")
        void testResponderAccess() {
            // Allowed
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.GET, Role.RESPONDER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents/1/status", HttpMethod.PATCH, Role.RESPONDER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.GET, Role.RESPONDER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/evidence/upload", HttpMethod.POST, Role.RESPONDER));

            // Forbidden (403)
            assertFalse(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.GET, Role.RESPONDER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/incidents/1/assignments", HttpMethod.POST, Role.RESPONDER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.POST, Role.RESPONDER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/response/allocations", HttpMethod.POST, Role.RESPONDER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/evidence/audit", HttpMethod.GET, Role.RESPONDER));
        }

        @Test
        @DisplayName("REPORTER permissions vs restrictions")
        void testReporterAccess() {
            // Allowed
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.POST, Role.REPORTER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/incidents", HttpMethod.GET, Role.REPORTER));
            assertTrue(authenticationFilter.isAuthorized("/api/v1/evidence/local/1/photo.jpg", HttpMethod.GET, Role.REPORTER));

            // Forbidden (403)
            assertFalse(authenticationFilter.isAuthorized("/api/v1/users", HttpMethod.GET, Role.REPORTER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/incidents/1/assignments", HttpMethod.POST, Role.REPORTER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/incidents/1/status", HttpMethod.PATCH, Role.REPORTER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/response/teams", HttpMethod.GET, Role.REPORTER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/evidence/1", HttpMethod.GET, Role.REPORTER));
            assertFalse(authenticationFilter.isAuthorized("/api/v1/evidence/upload", HttpMethod.POST, Role.REPORTER));
        }
    }
}
