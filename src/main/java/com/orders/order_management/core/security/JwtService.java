package com.orders.order_management.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.*;


@Service
public class JwtService {

    private final Key key;
    private final long expirationMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtService(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expirationMillis}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username, Collection<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles); // e.g., ["ADMIN","SALES","MANAGER"]
        claims.put("username", username);
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return parseAllClaims(token).getSubject();
    }

    public List<String> extractRoles(String token) {
        Object raw = parseAllClaims(token).get("roles");
        if (raw instanceof Collection<?> c) {
            List<String> roles = new ArrayList<>();
            for (Object o : c) roles.add(String.valueOf(o));
            return roles;
        }
        return List.of();
    }

    public boolean isTokenValid(String token, String username) {
        try {
            Claims claims = parseAllClaims(token);
            return username.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
                .getBody();
    }
}

