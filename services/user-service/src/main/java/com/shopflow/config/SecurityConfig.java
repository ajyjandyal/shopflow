package com.shopflow.config;

import com.shopflow.security.JwtService;
import com.shopflow.security.RestAccessDeniedHandler;
import com.shopflow.security.RestAuthenticationEntryPoint;
import com.shopflow.security.SecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        SecurityDefaults.apply(http, jwtService, entryPoint, accessDeniedHandler)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(SecurityDefaults.PUBLIC_INFRA_PATHS).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Only user-service handles passwords, so only it has a PasswordEncoder. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
