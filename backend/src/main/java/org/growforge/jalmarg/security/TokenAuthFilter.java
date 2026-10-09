package org.growforge.jalmarg.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.growforge.jalmarg.model.AppUser;
import org.growforge.jalmarg.model.AuthSession;
import org.growforge.jalmarg.repo.AuthSessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reads "Authorization: Bearer <token>", looks the token's hash up in auth_sessions and,
 * if the session is valid and the email is verified, marks the request as that user.
 * An invalid or expired token simply leaves the request anonymous; protected endpoints
 * then answer 401.
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthSessionRepository sessions;

    public TokenAuthFilter(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 20) {
            String token = header.substring(7).trim();
            sessions.findByTokenHash(Tokens.sha256(token)).ifPresent(s -> authenticate(s));
        }
        chain.doFilter(req, res);
    }

    private void authenticate(AuthSession s) {
        Instant now = Instant.now();
        if (s.getExpiresAt() == null || s.getExpiresAt().isBefore(now)) return;
        AppUser u = s.getUser();
        if (!u.isEmailVerified()) return;
        AuthUser principal = new AuthUser(u.getId(), u.getEmail(), u.getName(), u.getRole(), s.getId());
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        // record activity at most every 10 minutes
        if (s.getLastUsedAt() == null || s.getLastUsedAt().isBefore(now.minus(Duration.ofMinutes(10)))) {
            s.setLastUsedAt(now);
            sessions.save(s);
        }
    }
}
