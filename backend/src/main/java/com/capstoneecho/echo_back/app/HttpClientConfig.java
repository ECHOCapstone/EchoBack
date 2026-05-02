package com.capstoneecho.echo_back.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// 외부 HTTP 호출용 RestClient 빈을 정의하는 단일 진입점.
//
// 모델 서버(uvicorn) 는 HTTP/1.1 만 지원한다. JDK HttpClient 의 기본값은 HTTP/2 + h2c upgrade
// 시도라 multipart 본문이 모델 서버에서 정상 파싱되지 않을 수 있어 (Transfer-Encoding: chunked +
// Upgrade: h2c 헤더 조합), HTTP/1.1 로 못박아 보낸다.
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient modelRestClient(AppProperties properties) {
        var timeout = Duration.ofMillis(properties.modelServer().timeoutMs());
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.modelServer().baseUrl())
                .requestFactory(factory)
                .build();
    }
}
