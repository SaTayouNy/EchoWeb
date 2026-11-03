package org.example.echo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityBeansConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder(@Value("${app.auth.bcrypt.strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
