package org.growforge.jalmarg.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Who may call what. Reading the map, routes and weather is public; anything that writes
 * citizen data needs a verified account; authority tools need the AUTHORITY or ADMIN role.
 * Stateless: every request carries a bearer token (works the same for web and the Android app).
 */
@Configuration
public class SecurityConfig {

    @Value("${jalmarg.cors.origins}")
    private String origins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenAuthFilter tokenFilter) throws Exception {
        ObjectMapper json = new ObjectMapper();
        http
                .csrf(c -> c.disable()) // no cookies are used for auth, so CSRF does not apply
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, json, 401, "AUTH_REQUIRED",
                                "Please sign in to do this."))
                        .accessDeniedHandler((req, res, ex) -> write(res, json, 403, "FORBIDDEN",
                                "Your account is not allowed to do this.")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/authority/**").hasAnyRole("AUTHORITY", "ADMIN")
                        .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reports").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reports/*/confirm", "/api/reports/*/clear").authenticated()
                        .requestMatchers("/api/alerts/subscriptions", "/api/alerts/subscriptions/**").authenticated()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** The token filter runs only inside the security chain, not a second time as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<TokenAuthFilter> tokenFilterRegistration(TokenAuthFilter f) {
        FilterRegistrationBean<TokenAuthFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(Arrays.stream(origins.split("\\s*,\\s*")).filter(o -> !o.isBlank()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", c);
        return src;
    }

    private static void write(HttpServletResponse res, ObjectMapper json, int status, String code, String msg)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), Map.of("error", msg, "code", code));
    }
}
