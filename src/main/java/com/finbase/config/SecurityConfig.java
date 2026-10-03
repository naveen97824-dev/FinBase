package com.finbase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication is JWT-based and enforced by
 * {@link com.finbase.security.FinancierContextInterceptor}, not by Spring
 * Security's servlet filter chain. This disables Spring Boot's default
 * auto-configured chain (form login, HTTP Basic, the generated-password
 * warning) so it doesn't intercept requests before our own interceptor runs.
 *
 * <p>CSRF is disabled because this API has no server-rendered forms or
 * cookie-based session auth for state-changing requests to protect — every
 * request carries its own bearer token.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
