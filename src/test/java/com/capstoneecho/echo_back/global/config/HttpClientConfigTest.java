package com.capstoneecho.echo_back.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("test")
class HttpClientConfigTest {

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private RestClient restClient;

    @Autowired
    private AppProperties appProperties;

    @Test
    @DisplayName("HttpClient Bean uses HTTP/1.1 to dodge HTTP/2 multipart incompatibilities")
    void httpClientUsesHttp11() {
        assertThat(httpClient).isNotNull();
        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    @DisplayName("HttpClient connectTimeout matches AppProperties.modelServer.timeoutMs")
    void connectTimeoutMatchesProperty() {
        Duration expected = Duration.ofMillis(appProperties.modelServer().timeoutMs());
        assertThat(httpClient.connectTimeout()).hasValue(expected);
    }

    @Test
    @DisplayName("RestClient Bean is registered for HTTP collaborators")
    void restClientBeanRegistered() {
        assertThat(restClient).isNotNull();
    }
}
