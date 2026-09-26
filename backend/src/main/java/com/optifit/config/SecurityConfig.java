package com.optifit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import com.optifit.exception.ApiErrorResponse;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
        return http
                .authorizeHttpRequests(
                        a -> a.requestMatchers("/api/**", "/actuator/health").permitAll().anyRequest().denyAll())
                .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .exceptionHandling(e -> e.accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    json.writeValue(response.getWriter(), new ApiErrorResponse("SESSION_EXPIRED",
                            "Your session must be refreshed. Reload the page and try again."));
                })).build();
    }
}
