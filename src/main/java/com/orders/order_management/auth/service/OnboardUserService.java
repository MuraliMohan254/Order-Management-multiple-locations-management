package com.orders.order_management.auth.service;


import com.orders.order_management.auth.dto.OnboardUserRequest;
import com.orders.order_management.auth.dto.OnboardUserResponse;
import com.orders.order_management.auth.entity.User;
import com.orders.order_management.auth.repository.UserRepository;
import com.orders.order_management.core.enums.Role;
import com.orders.order_management.core.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardUserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public OnboardUserResponse registerUser(OnboardUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new UserAlreadyExistsException(req.getUsername());
        }

        String hashedPassword = passwordEncoder.encode(req.getPassword());

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(hashedPassword);
        // Add role(s)
        Set<Role> roles = new HashSet<>();
        if (req.getRole() != null) {
            roles.add(req.getRole());
        } else {
            roles.add(Role.SALES);
        }
        user.setRoles(roles);

        userRepository.save(user);


        return new OnboardUserResponse("User registered successfully", HttpStatus.CREATED);
    }
}

