package com.shopflow.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security settings every service shares. Each service then adds only its own
 * authorization rules, so the common parts can never drift apart between services.
 */
public final class SecurityDefaults {

    /** Infrastructure endpoints that are always public. */
    public static final String[] PUBLIC_INFRA_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health/**", "/actuator/info", "/error"
    };

    private SecurityDefaults() {
    }

    public static HttpSecurity apply(HttpSecurity http,
                                     JwtService jwtService,
                                     RestAuthenticationEntryPoint authenticationEntryPoint,
                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is handled once, at the API gateway. If services also added CORS
                // headers, browsers would receive them twice and reject the response.
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    }
}
