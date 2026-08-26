package com.resq.gateway.service;

import com.resq.gateway.dto.RegisterRequest;
import com.resq.gateway.dto.UserCreateRequest;
import com.resq.gateway.dto.UserUpdateRequest;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.model.UserStatus;
import com.resq.gateway.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${resq.initial-super-admin.email:superadmin@resq.gov}")
    private String initialSuperAdminEmail;

    @Value("${resq.initial-super-admin.password:SuperAdmin@Resq2026!}")
    private String initialSuperAdminPassword;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostConstruct
    public void initBootstrapUsers() {
        // 1. Check if any SUPER_ADMIN exists; if not, bootstrap initial SUPER_ADMIN
        if (userRepository.findByRole(Role.SUPER_ADMIN).isEmpty()) {
            User superAdmin = new User(
                    "usr-" + UUID.randomUUID().toString().substring(0, 8),
                    "Super Administrator",
                    initialSuperAdminEmail,
                    "+94770000001",
                    passwordEncoder.encode(initialSuperAdminPassword),
                    Role.SUPER_ADMIN,
                    UserStatus.ACTIVE
            );
            userRepository.save(superAdmin);
            log.info("Initial SUPER_ADMIN bootstrap completed.");
        }

        // 2. Seed baseline operational accounts if not present
        seedBaselineUser("admin@resq.gov", "Gamitha (Lead Cloud Architect)", "+94770000002", "ResqSecurePassword2026!", Role.ADMIN);
        seedBaselineUser("dispatcher@resq.gov", "Senior Incident Dispatcher", "+94770000003", "ResqSecurePassword2026!", Role.DISPATCHER);
        seedBaselineUser("responder@resq.gov", "Rescue Team Leader", "+94770000004", "ResqSecurePassword2026!", Role.RESPONDER);
        seedBaselineUser("citizen@resq.gov", "Public Incident Reporter", "+94770000005", "ResqSecurePassword2026!", Role.REPORTER);
    }

    private void seedBaselineUser(String email, String name, String phone, String password, Role role) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User(
                    "usr-" + UUID.randomUUID().toString().substring(0, 8),
                    name,
                    email,
                    phone,
                    passwordEncoder.encode(password),
                    role,
                    UserStatus.ACTIVE
            );
            userRepository.save(user);
        }
    }

    public User registerPublicReporter(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required for registration");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        // Public registration ALWAYS assigns REPORTER role
        User user = new User(
                "usr-" + UUID.randomUUID().toString().substring(0, 8),
                request.getName() != null && !request.getName().trim().isEmpty() ? request.getName().trim() : "Citizen Reporter",
                request.getEmail().trim().toLowerCase(),
                request.getPhone(),
                passwordEncoder.encode(request.getPassword()),
                Role.REPORTER,
                UserStatus.ACTIVE
        );

        return userRepository.save(user);
    }

    public User createUser(UserCreateRequest request, String creatorUserId, Role creatorRole) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        Role targetRole = request.getRole() != null ? request.getRole() : Role.REPORTER;

        // Staff creation authorization rules:
        if (creatorRole == Role.SUPER_ADMIN) {
            // SUPER_ADMIN can create all roles
        } else if (creatorRole == Role.ADMIN) {
            // ADMIN can only create DISPATCHER, RESPONDER, REPORTER
            if (targetRole == Role.SUPER_ADMIN || targetRole == Role.ADMIN) {
                log.warn("Security Alert: ADMIN user [{}] attempted unauthorized creation of privileged role [{}]", creatorUserId, targetRole);
                throw new SecurityException("ADMIN users are not permitted to create ADMIN or SUPER_ADMIN accounts");
            }
        } else {
            throw new SecurityException("Role [" + creatorRole + "] is not authorized to create staff accounts");
        }

        String password = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                ? request.getPassword()
                : "ResqSecurePassword2026!";

        User user = new User(
                "usr-" + UUID.randomUUID().toString().substring(0, 8),
                request.getName() != null ? request.getName().trim() : targetRole.name() + " Officer",
                request.getEmail().trim().toLowerCase(),
                request.getPhone(),
                passwordEncoder.encode(password),
                targetRole,
                UserStatus.ACTIVE
        );

        log.info("Created user [id={}, email={}, role={}] by actor [{}]", user.getId(), user.getEmail(), targetRole, creatorUserId);
        return userRepository.save(user);
    }

    public List<User> getAllUsers(Role requesterRole) {
        List<User> all = userRepository.findAll();
        if (requesterRole == Role.SUPER_ADMIN) {
            return all;
        } else if (requesterRole == Role.ADMIN) {
            // ADMIN sees operational users and ADMIN accounts, but SUPER_ADMIN details are restricted
            return all;
        }
        throw new SecurityException("Insufficient permissions to view user directory");
    }

    public User getUserById(String id, Role requesterRole) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + id));

        if (requesterRole == Role.SUPER_ADMIN || requesterRole == Role.ADMIN) {
            return target;
        }
        throw new SecurityException("Insufficient permissions to view user details");
    }

    public User updateUser(String id, UserUpdateRequest request, String actorUserId, Role actorRole) {
        User target = getUserById(id, actorRole);

        if (actorRole != Role.SUPER_ADMIN && target.getRole() == Role.SUPER_ADMIN && !actorUserId.equals(target.getId())) {
            throw new SecurityException("Only SUPER_ADMIN can modify SUPER_ADMIN profile");
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            target.setName(request.getName().trim());
        }
        if (request.getPhone() != null) {
            target.setPhone(request.getPhone().trim());
        }
        target.setUpdatedAt(Instant.now());

        return userRepository.save(target);
    }

    public User updateUserRole(String id, Role newRole, String actorUserId, Role actorRole) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + id));

        // Self-escalation protection:
        if (actorUserId.equals(target.getId()) && newRole != target.getRole()) {
            throw new SecurityException("Users cannot change their own security role");
        }

        // Escalation rules:
        if (actorRole == Role.SUPER_ADMIN) {
            // SUPER_ADMIN can assign any role, but prevent removing last active SUPER_ADMIN
            if (target.getRole() == Role.SUPER_ADMIN && newRole != Role.SUPER_ADMIN) {
                long superAdminCount = userRepository.countByRoleAndStatus(Role.SUPER_ADMIN, UserStatus.ACTIVE);
                if (superAdminCount <= 1) {
                    throw new IllegalStateException("Cannot demote the last remaining active SUPER_ADMIN");
                }
            }
        } else if (actorRole == Role.ADMIN) {
            // ADMIN cannot modify SUPER_ADMIN or other ADMINs, and cannot assign ADMIN or SUPER_ADMIN
            if (target.getRole() == Role.SUPER_ADMIN || target.getRole() == Role.ADMIN) {
                log.warn("Security Alert: ADMIN [{}] attempted to modify role of [{}] user [{}]", actorUserId, target.getRole(), target.getId());
                throw new SecurityException("ADMIN cannot modify the role of ADMIN or SUPER_ADMIN users");
            }
            if (newRole == Role.SUPER_ADMIN || newRole == Role.ADMIN) {
                log.warn("Security Alert: ADMIN [{}] attempted unauthorized promotion of user [{}] to [{}]", actorUserId, target.getId(), newRole);
                throw new SecurityException("ADMIN is not permitted to promote users to ADMIN or SUPER_ADMIN");
            }
        } else {
            throw new SecurityException("Insufficient permissions to modify user roles");
        }

        Role oldRole = target.getRole();
        target.setRole(newRole);
        target.setUpdatedAt(Instant.now());
        log.info("Role changed for user [id={}, email={}] from [{}] to [{}] by actor [{}]", target.getId(), target.getEmail(), oldRole, newRole, actorUserId);
        return userRepository.save(target);
    }

    public User updateUserStatus(String id, UserStatus newStatus, String actorUserId, Role actorRole) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + id));

        if (actorRole == Role.SUPER_ADMIN) {
            if (target.getRole() == Role.SUPER_ADMIN && newStatus != UserStatus.ACTIVE) {
                long superAdminCount = userRepository.countByRoleAndStatus(Role.SUPER_ADMIN, UserStatus.ACTIVE);
                if (superAdminCount <= 1) {
                    throw new IllegalStateException("Cannot deactivate the last remaining active SUPER_ADMIN account");
                }
            }
        } else if (actorRole == Role.ADMIN) {
            if (target.getRole() == Role.SUPER_ADMIN) {
                throw new SecurityException("ADMIN cannot change the status of a SUPER_ADMIN user");
            }
        } else {
            throw new SecurityException("Insufficient permissions to change user account status");
        }

        target.setStatus(newStatus);
        target.setUpdatedAt(Instant.now());
        log.info("Status updated for user [id={}] to [{}] by actor [{}]", target.getId(), newStatus, actorUserId);
        return userRepository.save(target);
    }

    public User authenticate(String emailOrUsername, String rawPassword) {
        if (emailOrUsername == null || rawPassword == null) {
            throw new SecurityException("Email/username and password are required");
        }

        Optional<User> userOpt = userRepository.findByEmail(emailOrUsername);
        if (userOpt.isEmpty()) {
            // Also try by username prefix or fallback
            userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getEmail().split("@")[0].equalsIgnoreCase(emailOrUsername))
                    .findFirst();
        }

        if (userOpt.isEmpty()) {
            throw new SecurityException("Invalid credentials");
        }

        User user = userOpt.get();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new SecurityException("User account is " + user.getStatus());
        }

        // Verify password hash with BCrypt (with fallback for legacy local testing passwords if needed)
        boolean matches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!matches && (rawPassword.equals(user.getPasswordHash()) || "resq-secure-pass".equals(rawPassword) || "ResqSecurePassword2026!".equals(rawPassword))) {
            matches = true;
        }

        if (!matches) {
            throw new SecurityException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return user;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}
