package com.capstoneecho.echo_back.global.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    private final AppProperties appProperties;

    public HttpClientConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public HttpClient httpClient() {
        Duration connectTimeout = Duration.ofMillis(appProperties.modelServer().timeoutMs());
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
    }

    @Bean
    public RestClient restClient(HttpClient httpClient) {
        Duration readTimeout = Duration.ofMillis(appProperties.modelServer().timeoutMs());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
