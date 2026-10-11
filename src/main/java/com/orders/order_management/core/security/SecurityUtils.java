package com.orders.order_management.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SecurityUtils {

    /**
     * Checks if the currently authenticated user has the ADMIN role.
     */
    public static boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a) 
                            || "ADMIN".equalsIgnoreCase(a) 
                            || "ROLE_SUPER_ADMIN".equalsIgnoreCase(a));
    }
    
    /**
     * Retrieves the outletId of the currently authenticated user from the request context.
     * <p>
     * - If user is ADMIN: allows query param 'outletId' or header 'X-Outlet-Id' to switch target outlet.
     *   Passing 'all' or 'consolidated' or omitting returns null (enabling multi-outlet consolidated view).
     * - If user is Non-ADMIN (Cashier, Waiter, Kitchen, etc.): query params and headers are STRICTLY IGNORED.
     *   The user's assigned outlet from their JWT token is always returned, ensuring total tenant isolation.
     * </p>
     * @return The outletId, or null if the user has no outlet (e.g. global Admin in consolidated mode) or is unauthenticated.
     */
    public static String getCurrentOutletId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        jakarta.servlet.http.HttpServletRequest req = attrs.getRequest();

        // 1. Admin permission check
        if (isCurrentUserAdmin()) {
            String overrideOutletId = req.getParameter("outletId");
            if (overrideOutletId == null || overrideOutletId.isBlank()) {
                overrideOutletId = req.getHeader("X-Outlet-Id");
            }
            if (overrideOutletId != null) {
                String trimmed = overrideOutletId.trim();
                if ("all".equalsIgnoreCase(trimmed) || "consolidated".equalsIgnoreCase(trimmed)
                        || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)
                        || trimmed.isEmpty()) {
                    return null;
                }
                return trimmed;
            }
            // Admin fallback: assigned attribute if present, else null
            Object outletId = req.getAttribute("outletId");
            return outletId != null ? outletId.toString() : null;
        }

        // 2. Strict tenant isolation for non-admin users
        Object outletId = req.getAttribute("outletId");
        return outletId != null ? outletId.toString() : null;
    }

    /**
     * Retrieves the username of the currently authenticated user.
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return "Staff";
    }
}

