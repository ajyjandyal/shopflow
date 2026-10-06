package com.shopflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.security.InternalApiProperties;
import com.shopflow.security.JwtService;
import com.shopflow.security.RestAccessDeniedHandler;
import com.shopflow.security.RestAuthenticationEntryPoint;
import com.shopflow.security.SecurityDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   InternalApiProperties internalApi,
                                                   ObjectMapper objectMapper) throws Exception {
        SecurityDefaults.apply(http, jwtService, entryPoint, accessDeniedHandler)
                .addFilterBefore(new InternalApiKeyFilter(internalApi.apiKey(), objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        // No user JWT on internal calls; InternalApiKeyFilter has already checked the key.
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(SecurityDefaults.PUBLIC_INFRA_PATHS).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
