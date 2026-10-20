package com.orders.order_management.core.security;


import com.orders.order_management.auth.entity.User;
import com.orders.order_management.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;

import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .map(u -> org.springframework.security.core.userdetails.User.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
                        .disabled(!u.isActive())
                        .authorities(
                                u.getRoles().stream()
                                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                                        .collect(Collectors.toSet())
                        )
                        .build()
                )
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

}

