package org.growforge.jalmarg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JalMargApplication {
    public static void main(String[] args) {
        SpringApplication.run(JalMargApplication.class, args);
    }
}
