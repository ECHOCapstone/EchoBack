package com.capstoneecho.echo_back.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        ModelServer modelServer,
        Llm llm,
        Storage storage,
        Tts tts,
        Stats stats
) {

    public record Jwt(String secret, long expirationMs) {}

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAgeSec
    ) {}

    public record ModelServer(String baseUrl, long timeoutMs) {}

    public record Llm(String provider) {}

    public record Storage(String localRoot) {}

    public record Tts(String provider) {}

    public record Stats(String zone) {}
}
