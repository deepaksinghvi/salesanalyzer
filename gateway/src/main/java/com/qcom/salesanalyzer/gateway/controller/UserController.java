package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.dto.CreateUserRequest;
import com.qcom.salesanalyzer.gateway.entity.User;
import com.qcom.salesanalyzer.gateway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private boolean isAdminOrSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String role = auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority()).orElse("");
        return role.equals("ROLE_SuperAdmin") || role.equals("ROLE_Admin");
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SuperAdmin"));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<User>> getUsersByTenant(@PathVariable UUID tenantId) {
        if (!isAdminOrSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getUsersByTenant(tenantId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable UUID userId) {
        if (!isAdminOrSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (!isAdminOrSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        if (!isAdminOrSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        userService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId, @RequestBody java.util.Map<String, String> body) {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        userService.resetPassword(userId, body.get("password"));
        return ResponseEntity.noContent().build();
    }
}
