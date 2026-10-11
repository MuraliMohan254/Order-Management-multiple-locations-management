package com.orders.order_management.auth.controller;


import com.orders.order_management.auth.dto.AuthRequest;
import com.orders.order_management.auth.dto.AuthResponse;
import com.orders.order_management.auth.dto.CreateUserRequest;
import com.orders.order_management.auth.dto.UpdateUserRequest;
import java.util.Collections;
import com.orders.order_management.auth.dto.ChangePasswordRequest;
import com.orders.order_management.auth.dto.SubscriptionCheckResult;
import com.orders.order_management.auth.dto.UsersResponse;
import com.orders.order_management.auth.entity.User;
import com.orders.order_management.auth.repository.UserRepository;
import com.orders.order_management.auth.service.SubscriptionService;
import com.orders.order_management.core.entity.Outlet;
import com.orders.order_management.core.repository.OutletRepository;
import com.orders.order_management.core.security.JwtService;
import com.orders.order_management.core.utils.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired
    private OutletRepository outletRepository;

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

        String jwtToken = jwtService.generateToken(
                req.getUsername(), 
                roles, 
                user.getOutlet() != null ? user.getOutlet().getId() : null
        );
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
            if (user.getOutlet() != null) {
                dto.setOutletId(user.getOutlet().getId());
                dto.setOutletName(user.getOutlet().getName());
            }
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setActive(user.isActive());
            dto.setCreatedAt(user.getCreatedAt());
            dto.setLastLogin(user.getLastLogin());

            String roleString = user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            dto.setRoles(user.getRoles());

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
    @Transactional
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
    @Transactional
    @PutMapping("/users/{userId}/password")
    @Operation(summary = "Reset Password", description = "Admin forces a password change for a specific user.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeUserPassword(
            @PathVariable Long userId,
            @RequestBody ChangePasswordRequest request) {

        if (request.getNewPassword() == null || request.getNewPassword().length() < 5) {
            throw new RuntimeException("Password must be at least 5 characters long");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Hash the new password before saving!
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }


    // Create User
    @Transactional
    @PostMapping("/users")
    @Operation(summary = "Create User", description = "Admin creates a new user.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsersResponse> createUser(@RequestBody CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists.");
        }
        if (request.getPassword() == null || request.getPassword().length() < 5) {
            throw new RuntimeException("Password must be at least 5 characters long");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(request.getRoles() != null ? request.getRoles() : Collections.singleton(com.orders.order_management.core.enums.Role.CASHIER))
                .active(true)
                .build();
                
        if (request.getOutletId() != null && !request.getOutletId().trim().isEmpty()) {
            Outlet outlet = outletRepository.findById(request.getOutletId().trim())
                    .orElseThrow(() -> new RuntimeException("Outlet not found with id: " + request.getOutletId()));
            user.setOutlet(outlet);
        }
        
        user = userRepository.saveAndFlush(user);

        String outletId = user.getOutlet() != null ? user.getOutlet().getId() : null;
        String outletName = user.getOutlet() != null ? user.getOutlet().getName() : null;

        return ResponseEntity.ok(UsersResponse.builder()
                .outletId(outletId)
                .outletName(outletName)
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build());
    }

    // Update User
    @Transactional
    @PutMapping("/users/{userId}")
    @Operation(summary = "Update User", description = "Admin updates user roles or username.")
    @Tag(name = "User Management")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsersResponse> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (request.getUsername() != null && !request.getUsername().isBlank() && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new RuntimeException("Username already exists.");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(request.getRoles());
        }
        if (request.getOutletId() != null && !request.getOutletId().trim().isEmpty()) {
            Outlet outlet = outletRepository.findById(request.getOutletId().trim())
                    .orElseThrow(() -> new RuntimeException("Outlet not found with id: " + request.getOutletId()));
            user.setOutlet(outlet);
        } else {
            user.setOutlet(null);
        }
        user = userRepository.saveAndFlush(user);

        String outletId = user.getOutlet() != null ? user.getOutlet().getId() : null;
        String outletName = user.getOutlet() != null ? user.getOutlet().getName() : null;

        return ResponseEntity.ok(UsersResponse.builder()
                .outletId(outletId)
                .outletName(outletName)
                .id(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build());
    }

    // 4. Delete User
    @Transactional
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


