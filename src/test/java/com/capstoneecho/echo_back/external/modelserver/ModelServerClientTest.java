package com.capstoneecho.echo_back.external.modelserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.PhonemeInventory;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeMismatchInspector;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeNormalizer;
import tools.jackson.databind.ObjectMapper;
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
import java.util.List;
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
                null, null, null, null, null, null, null);
        return new ModelServerClient(
                restClient, props, settings,
                new PhonemeNormalizer(),
                new PhonemeMismatchInspector(new PhonemeInventory(new ObjectMapper())));
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
    @DisplayName("transcribe 는 canonical 을 multipart 로 함께 보내고 perceived 를 받아 정규화한다")
    void transcribeSendsCanonicalAndReturnsPerceived() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        server.createContext("/transcribe", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBody, StandardCharsets.UTF_8));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\",\"AH\",\"L\",\"OW\"],"
                            + "\"peak_softmax\":[0.9,0.8,0.7,0.6],"
                            + "\"duration_sec\":1.23,"
                            + "\"speech_rate\":\"normal\","
                            + "\"speech_rate_ratio\":1.0,"
                            + "\"model_id\":\"echo-baseline\","
                            + "\"model_type\":\"echo\"}");
        });

        ModelServerClient client = newClient(5000);
        TranscribeResult result = client.transcribe(new byte[]{1, 2, 3}, "HH AH L OW");

        assertThat(capturedContentType.get()).contains("multipart/form-data");
        assertThat(capturedBody.get()).contains("name=\"audio\"");
        assertThat(capturedBody.get()).contains("name=\"canonical\"");
        assertThat(capturedBody.get()).contains("HH AH L OW");
        assertThat(result.perceived()).containsExactly("HH", "AH", "L", "OW");
        assertThat(result.peakSoftmax()).containsExactly(0.9, 0.8, 0.7, 0.6);
        assertThat(result.durationSec()).isEqualTo(1.23);
        assertThat(result.modelId()).isEqualTo("echo-baseline");
        assertThat(result.modelType()).isEqualTo("echo");
        assertThat(result.speechRateRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("transcribe 는 canonical 이 비면 multipart 에 포함하지 않는다")
    void transcribeOmitsCanonicalWhenBlank() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/transcribe", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(requestBody, StandardCharsets.UTF_8));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\"],\"peak_softmax\":[0.9],"
                            + "\"duration_sec\":0.5,\"speech_rate\":\"normal\"}");
        });

        ModelServerClient client = newClient(5000);
        TranscribeResult result = client.transcribe(new byte[]{1, 2}, "");

        assertThat(capturedBody.get()).contains("name=\"audio\"");
        assertThat(capturedBody.get()).doesNotContain("name=\"canonical\"");
        assertThat(capturedBody.get()).doesNotContain("name=\"model\"");
        assertThat(result.perceived()).containsExactly("HH");
    }

    @Test
    @DisplayName("transcribe 는 어드민이 고른 모델 id 를 form part 로 함께 보낸다")
    void transcribeSendsSelectedModel() {
        when(settings.getOrDefault(ModelServerSettingKeys.MODEL, "")).thenReturn("echo-wav2vec2-film");
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/transcribe", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\"],\"peak_softmax\":[],"
                            + "\"duration_sec\":0.5,\"speech_rate\":\"normal\"}");
        });

        ModelServerClient client = newClient(5000);
        client.transcribe(new byte[]{1, 2}, "");

        assertThat(capturedBody.get()).contains("name=\"model\"");
        assertThat(capturedBody.get()).contains("echo-wav2vec2-film");
    }

    @Test
    @DisplayName("transcribe(keepSilence=true) 는 keep_silence form part 를 함께 보낸다")
    void transcribeSendsKeepSilence() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/transcribe", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200,
                    "{\"perceived\":[\"HH\"],\"peak_softmax\":[0.9],"
                            + "\"duration_sec\":0.5,\"speech_rate\":\"normal\"}");
        });

        ModelServerClient client = newClient(5000);
        client.transcribe(new byte[]{1, 2}, "HH", true);

        assertThat(capturedBody.get()).contains("name=\"keep_silence\"");
        assertThat(capturedBody.get()).contains("true");
    }

    @Test
    @DisplayName("g2p 는 JSON {text} 를 보내고 words[] 를 CanonicalWord 로 매핑한다")
    void g2pPostsJsonAndMapsCanonicalWords() {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        server.createContext("/g2p", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respondJson(exchange, 200,
                    "{\"words\":["
                            + "{\"word\":\"the\",\"phonemes\":[\"DH\",\"AH\"]},"
                            + "{\"word\":\"event\",\"phonemes\":[\"IH\",\"V\",\"EH\",\"N\",\"T\"]}]}");
        });

        ModelServerClient client = newClient(5000);
        List<CanonicalWord> baseline = client.g2p("the event");

        assertThat(capturedContentType.get()).contains("application/json");
        assertThat(capturedBody.get()).contains("\"text\"").contains("the event");
        assertThat(baseline).hasSize(2);
        assertThat(baseline.get(0).word()).isEqualTo("the");
        assertThat(baseline.get(0).phonemes()).containsExactly("DH", "AH");
        assertThat(baseline.get(1).word()).isEqualTo("event");
        assertThat(baseline.get(1).phonemes()).containsExactly("IH", "V", "EH", "N", "T");
    }

    @Test
    @DisplayName("g2p 는 빈 text 면 /g2p 를 호출하지 않고 빈 리스트를 돌려준다")
    void g2pSkipsWhenBlank() {
        ModelServerClient client = newClient(5000);
        assertThat(client.g2p("")).isEmpty();
        assertThat(client.g2p(null)).isEmpty();
    }

    @Test
    @DisplayName("g2p 응답에 words 가 비면 MODEL_SERVER_ERROR")
    void g2pEmptyWordsMapsToServerError() {
        server.createContext("/g2p", exchange -> respondJson(exchange, 200, "{\"words\":[]}"));

        ModelServerClient client = newClient(5000);
        assertThatThrownBy(() -> client.g2p("hi"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR));
    }

    @Test
    @DisplayName("g2p 5xx 는 BusinessException(MODEL_SERVER_ERROR, 502) 로 매핑된다")
    void g2pServerErrorMapsTo502() {
        server.createContext("/g2p", (HttpHandler) exchange -> {
            byte[] body = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        ModelServerClient client = newClient(5000);
        assertThatThrownBy(() -> client.g2p("hi"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(502);
                });
    }

    @Test
    @DisplayName("g2p 읽기 타임아웃은 BusinessException(MODEL_SERVER_UNAVAILABLE, 503) 로 매핑된다")
    void g2pTimeoutMapsTo503() {
        server.createContext("/g2p", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respondJson(exchange, 200, "{\"words\":[]}");
        });

        ModelServerClient client = newClient(200);
        assertThatThrownBy(() -> client.g2p("hi"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(503);
                });
    }

    @Test
    @DisplayName("models 는 /models JSON 을 ModelCatalog 로 매핑한다")
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
    @DisplayName("5xx 응답은 BusinessException(MODEL_SERVER_ERROR, 502) 로 매핑된다")
    void serverErrorMapsTo502ModelServerError() {
        server.createContext("/transcribe", (HttpHandler) exchange -> {
            byte[] body = "internal error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        ModelServerClient client = newClient(5000);
        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2}, "HH"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(502);
                });
    }

    @Test
    @DisplayName("읽기 타임아웃은 BusinessException(MODEL_SERVER_UNAVAILABLE, 503) 로 매핑된다")
    void timeoutMapsTo503ModelServerUnavailable() {
        server.createContext("/transcribe", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respondJson(exchange, 200,
                    "{\"perceived\":[],\"peak_softmax\":[],\"duration_sec\":0,\"speech_rate\":\"normal\"}");
        });

        ModelServerClient client = newClient(200);
        assertThatThrownBy(() -> client.transcribe(new byte[]{1, 2}, "HH"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE);
                    assertThat(ex.getCode().getStatusCode()).isEqualTo(503);
                });
    }
}
