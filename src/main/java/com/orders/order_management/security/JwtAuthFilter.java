package com.orders.order_management.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // If not already authenticated, validate and set context
        if (username != null && org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                List<SimpleGrantedAuthority> authorities =
                        jwtService.extractRoles(jwt).stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}

