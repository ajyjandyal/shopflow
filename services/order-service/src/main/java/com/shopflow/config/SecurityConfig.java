package com.shopflow.config;

import com.shopflow.security.JwtService;
import com.shopflow.security.RestAccessDeniedHandler;
import com.shopflow.security.RestAuthenticationEntryPoint;
import com.shopflow.security.SecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
                        .requestMatchers(SecurityDefaults.PUBLIC_INFRA_PATHS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }
}
