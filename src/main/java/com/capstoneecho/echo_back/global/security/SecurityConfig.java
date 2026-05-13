package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtProvider jwtProvider) {
        return new JwtAuthFilter(jwtProvider);
    }

    @Bean
    public JwtAuthEntryPoint jwtAuthEntryPoint(ObjectMapper objectMapper) {
        return new JwtAuthEntryPoint(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            JwtAuthEntryPoint jwtAuthEntryPoint
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Liveness/readiness probes are open so load balancers and
                        // orchestrators can ping the app without credentials.
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/health",
                                "/api/tts",
                                "/error",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        // Other Actuator endpoints (info/metrics/...) carry
                        // operational metadata and stay behind ROLE_ADMIN until
                        // we split them off onto a separate management port.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
