package com.capstoneecho.echo_back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// 모델 서버(uvicorn) 가 HTTP/1.1 만 지원해서 JDK HttpClient 의 기본값(HTTP/2 + h2c upgrade)
// 으로 보내면 multipart 본문이 깨진다. 그래서 HTTP/1.1 로 박아 둔 RestClient 를 만들어 둔다.
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
