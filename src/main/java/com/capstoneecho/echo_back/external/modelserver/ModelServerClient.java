package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeAligner;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeMismatchInspector;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeNormalizer;
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
    private final PhonemeNormalizer phonemeNormalizer;
    private final PhonemeAligner phonemeAligner;
    private final PhonemeMismatchInspector phonemeMismatchInspector;

    public ModelServerClient(
            RestClient restClient,
            AppProperties appProperties,
            SettingsService settings,
            PhonemeNormalizer phonemeNormalizer,
            PhonemeAligner phonemeAligner,
            PhonemeMismatchInspector phonemeMismatchInspector) {
        this.restClient = restClient;
        this.appProperties = appProperties;
        this.settings = settings;
        this.phonemeNormalizer = phonemeNormalizer;
        this.phonemeAligner = phonemeAligner;
        this.phonemeMismatchInspector = phonemeMismatchInspector;
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

    // 모델 서버 응답의 perceived 를 표준 ARPABET 음소로 정규화한 뒤, 정답과 백엔드에서 다시 정렬해
    // errors / per 를 일관되게 재계산한다. canonical 이 비어 있으면 정렬 기반이 없으므로 모델 응답을 그대로 둔다.
    private AnalyzeResult toResult(AnalyzeWire wire) {
        PhonemeNormalizer.NormalizedPhonemes normalized =
                phonemeNormalizer.normalize(wire.perceived(), wire.peakSoftmax());
        List<String> normalizedPerceived = normalized.phonemes();
        List<Double> normalizedSoftmax = normalized.peakSoftmax();

        List<String> canonical = wire.canonical();
        boolean canRealign = canonical != null && !canonical.isEmpty();

        List<AnalyzeError> errors;
        Double per;
        if (canRealign) {
            PhonemeAligner.AlignmentResult alignment = phonemeAligner.align(canonical, normalizedPerceived);
            errors = alignment.errors();
            per = alignment.per();
        } else {
            errors = wire.errors() == null ? List.of() : wire.errors();
            per = wire.per();
        }

        // 정규화 + 정렬 결과의 음소 집합이 표준 ARPABET 밖이면 한 줄 WARN — 운영자가 tmux 로그로
        // 새 비표준 케이스를 모니터링하고 PhonemeNormalizer 사전에 반영할 수 있게 한다.
        phonemeMismatchInspector.inspect(canonical, normalizedPerceived);

        return new AnalyzeResult(
                normalizedPerceived,
                canonical,
                normalizedSoftmax,
                wire.alignment(),
                errors,
                per,
                wire.durationSec() == null ? 0.0 : wire.durationSec(),
                wire.speechRate()
        );
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
