package org.growforge.jalmarg.security;

import org.growforge.jalmarg.model.Enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user as seen by controllers (no password hash, nothing secret). */
public record AuthUser(Long id, String email, String name, Role role, Long sessionId) {

    /** Current user, or null when the request is anonymous. */
    public static AuthUser current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthUser u ? u : null;
    }
}
