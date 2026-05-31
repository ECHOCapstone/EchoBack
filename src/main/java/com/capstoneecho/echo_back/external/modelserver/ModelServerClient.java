package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ModelServerClient {

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final SettingsService settings;

    public ModelServerClient(
            RestClient restClient, AppProperties appProperties, SettingsService settings) {
        this.restClient = restClient;
        this.appProperties = appProperties;
        this.settings = settings;
    }

    public G2pResult g2p(String text) {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("text", textPart("text", text == null ? "" : text));
        return execute("/g2p", body, G2pResult.class);
    }

    public AnalyzeResult analyze(byte[] audioBytes, String canonicalArpabetSpaceSep) {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("audio", audioPart(audioBytes));
        if (canonicalArpabetSpaceSep != null && !canonicalArpabetSpaceSep.isBlank()) {
            body.add("canonical", textPart("canonical", canonicalArpabetSpaceSep));
        }
        // 어드민이 고른 음소인식 모델 id 를 함께 보낸다. 비어 있으면 모델 서버 기본 모델을 쓴다.
        String model = settings.getOrDefault(ModelServerSettingKeys.MODEL, "");
        if (!model.isBlank()) {
            body.add("model", textPart("model", model));
        }
        AnalyzeWire wire = execute("/analyze", body, AnalyzeWire.class);
        return toResult(wire);
    }

    // 선택 가능한 음소인식 모델 후보 + 활성 모델 (모델 서버 /models).
    public ModelCatalog models() {
        String url = appProperties.modelServer().baseUrl() + "/models";
        try {
            return restClient.get().uri(url).retrieve().body(ModelCatalog.class);
        } catch (ResourceAccessException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, ex.getMessage());
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, ex.getResponseBodyAsString());
        }
    }

    private static HttpEntity<String> textPart(String name, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.formData().name(name).build());
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new HttpEntity<>(value, headers);
    }

    private static HttpEntity<ByteArrayResource> audioPart(byte[] audioBytes) {
        ByteArrayResource resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.formData()
                        .name("audio")
                        .filename("audio.wav")
                        .build());
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        return new HttpEntity<>(resource, headers);
    }

    private <T> T execute(
            String path,
            MultiValueMap<String, HttpEntity<?>> body,
            Class<T> responseType
    ) {
        String url = appProperties.modelServer().baseUrl() + path;
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (ResourceAccessException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, ex.getMessage());
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, ex.getResponseBodyAsString());
        }
    }

    private static AnalyzeResult toResult(AnalyzeWire wire) {
        return new AnalyzeResult(
                wire.perceived(),
                wire.canonical(),
                wire.peakSoftmax(),
                wire.alignment(),
                wire.errors(),
                wire.per(),
                wire.durationSec() == null ? 0.0 : wire.durationSec(),
                wire.speechRate()
        );
    }

    // 모델 서버 /analyze 응답은 snake_case 키를 쓰므로 @JsonAlias 로 camelCase 와 양쪽을 모두 받는다.
    // speechRate 는 fast / normal / slow 분류, 누락 시 SpeechRate.fromString 이 NORMAL 로 폴백한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalyzeWire(
            List<String> perceived,
            List<String> canonical,
            @JsonAlias("peak_softmax") List<Double> peakSoftmax,
            List<Object> alignment,
            List<AnalyzeError> errors,
            Double per,
            @JsonAlias("duration_sec") Double durationSec,
            @JsonAlias("speech_rate") SpeechRate speechRate
    ) {}
}
