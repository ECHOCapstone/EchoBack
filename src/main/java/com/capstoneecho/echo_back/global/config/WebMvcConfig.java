package com.capstoneecho.echo_back.global.config;

import com.capstoneecho.echo_back.global.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(AppProperties appProperties,
                        CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.appProperties = appProperties;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.Cors cors = appProperties.cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(cors.allowedMethods());
        config.setExposedHeaders(cors.exposedHeaders());
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(cors.allowCredentials());
        config.setMaxAge(cors.maxAgeSec());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
