package com.capstoneecho.echo_back.global.security;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.global.security.oauth2.CustomOAuth2UserService;
import com.capstoneecho.echo_back.global.security.oauth2.CustomOidcUserService;
import com.capstoneecho.echo_back.global.security.oauth2.OAuth2LoginFailureHandler;
import com.capstoneecho.echo_back.global.security.oauth2.OAuth2LoginSuccessHandler;
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
            JwtAuthEntryPoint jwtAuthEntryPoint,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler
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
                                "/actuator/health/**",
                                // Spring 이 자동 등록하는 OAuth2 authorize / callback 경로.
                                // 인증 전 단계이므로 반드시 permitAll 이어야 한다.
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        // Other Actuator endpoints (info/metrics/...) carry
                        // operational metadata and stay behind ROLE_ADMIN until
                        // we split them off onto a separate management port.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        // OIDC (openid scope 포함) 와 비 OIDC provider 양쪽 모두 mapper 가 실행되도록
                        // userService / oidcUserService 모두 등록한다. Google 은 openid 가 있으므로
                        // 실제로는 OIDC 경로가 사용되지만, 향후 Kakao 같은 비 OIDC provider 도 대비.
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
