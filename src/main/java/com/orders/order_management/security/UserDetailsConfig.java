package com.orders.order_management.security;

import com.orders.order_management.repos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.userdetails.User;

import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .map(u -> User.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
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

