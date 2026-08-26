package com.resq.gateway.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resq.gateway.model.Role;
import com.resq.gateway.model.User;
import com.resq.gateway.model.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final Map<String, User> userMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path storagePath;

    public UserRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        String userHome = System.getProperty("user.home");
        this.storagePath = Paths.get(userHome, ".resq", "users.json");
        loadFromDisk();
    }

    private synchronized void loadFromDisk() {
        try {
            if (Files.exists(storagePath)) {
                byte[] data = Files.readAllBytes(storagePath);
                if (data.length > 0) {
                    List<User> users = objectMapper.readValue(data, new TypeReference<List<User>>() {});
                    for (User user : users) {
                        if (user.getId() != null && user.getEmail() != null) {
                            userMap.put(user.getId(), user);
                        }
                    }
                    log.info("Loaded {} user records from storage: {}", userMap.size(), storagePath);
                }
            }
        } catch (Exception e) {
            log.warn("Could not read users from {}: {}", storagePath, e.getMessage());
        }
    }

    private synchronized void saveToDisk() {
        try {
            Files.createDirectories(storagePath.getParent());
            byte[] data = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new ArrayList<>(userMap.values()));
            Files.write(storagePath, data);
        } catch (Exception e) {
            log.warn("Failed to persist users to {}: {}", storagePath, e.getMessage());
        }
    }

    public Optional<User> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(userMap.get(id));
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String target = email.trim().toLowerCase();
        return userMap.values().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(target))
                .findFirst();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public List<User> findAll() {
        return new ArrayList<>(userMap.values());
    }

    public List<User> findByRole(Role role) {
        return userMap.values().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    public synchronized User save(User user) {
        userMap.put(user.getId(), user);
        saveToDisk();
        return user;
    }

    public synchronized void deleteById(String id) {
        userMap.remove(id);
        saveToDisk();
    }

    public long countByRoleAndStatus(Role role, UserStatus status) {
        return userMap.values().stream()
                .filter(u -> u.getRole() == role && u.getStatus() == status)
                .count();
    }
}
