package com.capstoneecho.echo_back.external.modelserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeAligner;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeMismatchInspector;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeNormalizer;
import com.capstoneecho.echo_back.external.modelserver.support.WeightedPerCalculator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ModelServerClientTest {

    private HttpServer server;
    private String baseUrl;
    private SettingsService settings;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        // 기본: 선택된 모델 없음 → analyze 가 model 파라미터를 붙이지 않는다.
        settings = mock(SettingsService.class);
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ModelServerClient newClient(long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        AppProperties props = new AppProperties(
                null,
                null,
                new AppProperties.ModelServer(baseUrl, timeoutMs),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null, null, null, null);
        // 정렬 / 채점 정책 분리: 본 통합 테스트는 client 의 HTTP 경로만 검증하므로 uniform 정책으로 둔다.
        WeightedPerCalculator uniformWeightedPer = new WeightedPerCalculator(
                new AppProperties(null, null, null, null, null, null, null, null,
                        new AppProperties.Scoring(java.util.List.of(), 1.0, 1.0, 1.0),
                        null,
                        null, null, null, null, null));
        return new ModelServerClient(
                restClient, props, settings,
                new PhonemeNormalizer(), new PhonemeAligner(uniformWeightedPer), new PhonemeMismatchInspector());
    }

    private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Test
    @DisplayName("g2p maps camelCase JSON to G2pResult")
    void g2pMapsCamelCase() {
        server.createContext("/g2p", exchange ->
                respondJson(exchange, 200,
                        "{\"phonemes\":\"HH AH L OW\","
                                + "\"words\":[{\"word\":\"hello\","
                                + "\"phonemes\":[\"HH\",\"AH\",\"L\",\"OW\"]}]}"));

        ModelServerClient client = newClient(5000);
        G2pResult result = client.g2p("hello");

        assertThat(result.phonemes()).isEqualTo("HH AH L OW");
        assertThat(result.words()).hasSize(1);
        assertThat(result.words().get(0).word()).isEqualTo("hello");
        assertThat(result.words().get(0).phonemes())
                .containsExactly("HH", "AH", "L", "OW");
    }

    @Test
    @DisplayName("analyze: canonical 이 있으면 백엔드가 정규화된 perceived 로 errors / per 를 재계산한다")
    void analyzeMultipartCarriesCanonical() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        server.createContext("/analyze", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBody, StandardCharsets.UTF_8));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            // 모델 서버가 보낸 errors / per 는 의도적으로 잘못된 표기 ("delete") 와 부정확한 값으로 둔다 —
            // 백엔드가 PhonemeAligner 로 재계산하면 표준 "deletion" 표기와 정확한 per 가 나와야 한다.
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\",\"AH\"],"
                            + "\"canonical\":[\"HH\",\"AH\",\"L\",\"OW\"],"
                            + "\"peakSoftmax\":[0.9,0.8],"
                            + "\"alignment\":[],"
                            + "\"errors\":[{\"op\":\"delete\","
                            + "\"canonicalIndex\":2,"
                            + "\"perceived\":null,"
                            + "\"canonical\":\"L\"}],"
                            + "\"per\":0.5,"
                            + "\"durationSec\":1.23}");
        });

        ModelServerClient client = newClient(5000);
        AnalyzeResult result = client.analyze(new byte[]{1, 2, 3}, "HH AH L OW");

        assertThat(capturedContentType.get()).contains("multipart/form-data");
        assertThat(capturedBody.get()).contains("name=\"audio\"");
        assertThat(capturedBody.get()).contains("name=\"canonical\"");
        assertThat(capturedBody.get()).contains("HH AH L OW");

        assertThat(result.perceived()).containsExactly("HH", "AH");
        assertThat(result.canonical()).containsExactly("HH", "AH", "L", "OW");
        assertThat(result.peakSoftmax()).containsExactly(0.9, 0.8);
        // 재계산 결과: canonical 길이 4 vs perceived 길이 2 → 마지막 두 canonical 음소가 deletion.
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).extracting(AnalyzeError::op)
                .containsExactly("deletion", "deletion");
        assertThat(result.errors().get(0).canonical()).isEqualTo("L");
        assertThat(result.errors().get(0).canonicalIndex()).isEqualTo(2);
        assertThat(result.errors().get(1).canonical()).isEqualTo("OW");
        assertThat(result.errors().get(1).canonicalIndex()).isEqualTo(3);
        assertThat(result.per()).isEqualTo(0.5);
        assertThat(result.durationSec()).isEqualTo(1.23);
    }

    @Test
    @DisplayName("analyze omits canonical part when blank")
    void analyzeOmitsCanonicalWhenBlank() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/analyze", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBody, StandardCharsets.UTF_8));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\"],"
                            + "\"canonical\":null,"
                            + "\"peakSoftmax\":[0.9],"
                            + "\"alignment\":[],"
                            + "\"errors\":[],"
                            + "\"per\":null,"
                            + "\"durationSec\":0.5}");
        });

        ModelServerClient client = newClient(5000);
        AnalyzeResult result = client.analyze(new byte[]{1, 2}, "");

        assertThat(capturedBody.get()).contains("name=\"audio\"");
        assertThat(capturedBody.get()).doesNotContain("name=\"canonical\"");
        assertThat(capturedBody.get()).doesNotContain("name=\"model\"");
        assertThat(result.canonical()).isNull();
        assertThat(result.per()).isNull();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("analyze sends selected model id as a 'model' form part")
    void analyzeSendsSelectedModel() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-wav2vec2-film");
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/analyze", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\"],\"canonical\":null,\"peakSoftmax\":[],"
                            + "\"alignment\":[],\"errors\":[],\"per\":null,\"durationSec\":0.5}");
        });

        ModelServerClient client = newClient(5000);
        client.analyze(new byte[]{1, 2}, "");

        assertThat(capturedBody.get()).contains("name=\"model\"");
        assertThat(capturedBody.get()).contains("echo-wav2vec2-film");
    }

    @Test
    @DisplayName("models maps /models JSON to ModelCatalog")
    void modelsMapsCatalog() {
        server.createContext("/models", exchange ->
                respondJson(exchange, 200,
                        "{\"active\":\"echo-baseline\",\"models\":["
                                + "{\"id\":\"echo-baseline\",\"label\":\"ECHO\",\"type\":\"echo\"},"
                                + "{\"id\":\"slplab\",\"label\":\"slplab\",\"type\":\"slplab\"}]}"));

        ModelServerClient client = newClient(5000);
        ModelCatalog catalog = client.models();

        assertThat(catalog.active()).isEqualTo("echo-baseline");
        assertThat(catalog.safeModels()).hasSize(2);
        assertThat(catalog.safeModels().get(0).id()).isEqualTo("echo-baseline");
        assertThat(catalog.safeModels().get(1).type()).isEqualTo("slplab");
    }

    @Test
    @DisplayName("5xx response maps to BusinessException(MODEL_SERVER_ERROR) — 502")
    void serverErrorMapsTo502ModelServerError() {
        server.createContext("/g2p", (HttpHandler) exchange -> {
            byte[] body = "internal error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        ModelServerClient client = newClient(5000);
        assertThatThrownBy(() -> client.g2p("hello"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(502);
                });
    }

    @Test
    @DisplayName("read timeout maps to BusinessException(MODEL_SERVER_UNAVAILABLE) — 503")
    void timeoutMapsTo503ModelServerUnavailable() {
        server.createContext("/g2p", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respondJson(exchange, 200, "{\"phonemes\":\"\",\"words\":[]}");
        });

        ModelServerClient client = newClient(200);
        assertThatThrownBy(() -> client.g2p("hello"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(503);
                });
    }
}
