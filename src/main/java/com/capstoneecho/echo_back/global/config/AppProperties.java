package com.capstoneecho.echo_back.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 어플리케이션 설정을 한 곳에서 노출하는 record. application*.yaml 의 app.* 키를 그대로 매핑한다.
// 프로파일별 yaml 이 같은 record 를 다른 값으로 채운다.
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        ModelServer modelServer,
        Llm llm,
        Storage storage,
        Tts tts,
        Stats stats,
        Auth auth
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

    // 인증 관련 외부화. demoGoogle 은 OAuth2 시연 사용자의 식별자를 환경별로 바꿀 수 있게 해 둔다.
    public record Auth(DemoGoogle demoGoogle) {

        public record DemoGoogle(String email, String nickname) {}
    }
}
