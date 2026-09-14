package com.orders.order_management.controller;

import com.orders.order_management.dto.user.*;
import com.orders.order_management.entity.user.User;
import com.orders.order_management.repos.user.UserRepository;
import com.orders.order_management.security.JwtService;
import com.orders.order_management.service.user.SubscriptionService;
import com.orders.order_management.utils.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;



@RestController
@RequestMapping("${api.prefix}")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SubscriptionService subscriptionService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    @Operation(
            operationId = "User Login",
            summary = "User Login",
            description = "Authenticates a user with username and password and returns a JWT token for authorized access to the CRM system."
    )
    @Tag(name = "User Management")
    public AuthResponse login(@RequestBody AuthRequest req) {

        // A. Authenticate first (If this fails, code stops here)
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );

        //B. Check subscription
//        SubscriptionCheckResult subscriptionCheckResult = subscriptionService.checkSubscription();
//
//        // STRICT CHECK: If locked, block login completely
//        if (subscriptionCheckResult.isExpired()) {
//            throw new DisabledException("Subscription Expired on: " + subscriptionCheckResult.getExpiry_date()+" Please Renew!!");
//        }
        // C. Fetch the User Entity
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found")); // Should verify via auth, but good safety
        if (!user.isActive()){
            throw new DisabledException("User is inactive. Please contact your admin.");
        }
        // D. Update Last Login Time (UTC)
        user.setLastLogin(Instant.now());
        userRepository.save(user);

        // E. Generate Token (Optional: Pass 'user' object if you updated JwtService earlier)
        var roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .collect(Collectors.toSet());

        String jwtToken = jwtService.generateToken(req.getUsername(), roles);
        // If you updated JwtService to accept email/fullName, pass 'user' here instead of req.getUsername()
        return new AuthResponse(jwtToken, "ACTIVE", "26-05-2100","26-05-2100");
    }

    // 1. Fetch All Users
    @GetMapping("fetch/all-users")
    @Operation(summary = "Get All Users", description = "Fetches a paginated list of all registered users.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaginatedResponse<UsersResponse>> getAllUsers(
            @RequestParam(required = false) String searchTerm,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "username") Pageable pageable
    ) {
        // 1. Fetch paginated data from DB
        Page<User> userPage = userRepository.searchUsers(searchTerm, pageable);

        // 2. Map Entities to DTOs
        List<UsersResponse> dtoList = userPage.getContent().stream().map(user -> {
            UsersResponse dto = new UsersResponse();
            dto.setUserId(user.getId().intValue());
            dto.setUserName(user.getUsername());
            dto.setActive(user.isActive());
            dto.setCreatedAt(user.getCreatedAt());
            dto.setLastLogin(user.getLastLogin());

            String roleString = user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            dto.setRole(roleString);

            return dto;
        }).collect(Collectors.toList());

        // 3. Wrap in your custom PaginatedResponse
        PaginatedResponse<UsersResponse> response = PaginatedResponse.<UsersResponse>builder()
                .data(dtoList)
                .total(userPage.getTotalElements())
                .page(userPage.getNumber())
                .pageSize(userPage.getSize())
                .hasMore(userPage.hasNext())
                .build();

        return ResponseEntity.ok(response);
    }

    // 2. Update Active Status
    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Toggle User Status", description = "Enable or disable a user account.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserStatus(@PathVariable Long userId, @RequestParam boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(active);
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    // 3. Change Password (Admin Reset)
    @PutMapping("/users/{userId}/password")
    @Operation(summary = "Reset Password", description = "Admin forces a password change for a specific user.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeUserPassword(
            @PathVariable Long userId,
            @RequestBody ChangePasswordRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Hash the new password before saving!
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    // 4. Delete User
    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete User", description = "Permanently removes a user from the database.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {

        // Check if user exists first
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Optional: Prevent deleting yourself (if you want this safety check)
         String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
         if (user.getUsername().equals(currentUsername)) {
            throw new RuntimeException("You cannot delete your own account.");
         }

        userRepository.delete(user);

        return ResponseEntity.ok().build();
    }
}

