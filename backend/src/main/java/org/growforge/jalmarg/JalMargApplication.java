package org.growforge.jalmarg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// No default in-memory user: accounts live in the database (AuthService)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class JalMargApplication {
    public static void main(String[] args) {
        SpringApplication.run(JalMargApplication.class, args);
    }
}
