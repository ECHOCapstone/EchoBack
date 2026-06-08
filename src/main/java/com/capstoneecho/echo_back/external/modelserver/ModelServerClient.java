package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeMismatchInspector;
import com.capstoneecho.echo_back.external.modelserver.support.PhonemeNormalizer;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

// 모델 서버 contract:
//   POST /transcribe (multipart: audio, canonical, model?, keep_silence?)
//     -> { perceived, peak_softmax, duration_sec, speech_rate, speech_rate_ratio, model_id, model_type }
//   POST /g2p (application/json: { text })
//     -> { words: [{ word, phonemes: [...] }] }
// /g2p 는 CMU 기반 결정적 baseline canonical 을 돌려준다. 강세 strip + 39 productive 인벤토리 정규화는
// 모델 서버 책임. 백엔드는 이 baseline 을 LLM refine 단계의 입력으로 흘려보낸다.
@Component
public class ModelServerClient {

    private final RestClient restClient;
    private final AppProperties appProperties;
    private final SettingsService settings;
    private final PhonemeNormalizer phonemeNormalizer;
    private final PhonemeMismatchInspector phonemeMismatchInspector;

    public ModelServerClient(
            RestClient restClient,
            AppProperties appProperties,
            SettingsService settings,
            PhonemeNormalizer phonemeNormalizer,
            PhonemeMismatchInspector phonemeMismatchInspector) {
        this.restClient = restClient;
        this.appProperties = appProperties;
        this.settings = settings;
        this.phonemeNormalizer = phonemeNormalizer;
        this.phonemeMismatchInspector = phonemeMismatchInspector;
    }

    // 음성 인식만 받는다. canonical 은 모델 서버가 정렬에 참고할 수 있도록 함께 보내지만, alignment / errors /
    // 점수 계산은 모두 백엔드 (LLM 피드백 호출) 가 처리한다.
    public TranscribeResult transcribe(byte[] audioBytes, String canonicalArpabetSpaceSep) {
        return transcribe(audioBytes, canonicalArpabetSpaceSep, null);
    }

    // keepSilence 가 null 이면 모델 서버 기본값 사용.
    public TranscribeResult transcribe(
            byte[] audioBytes, String canonicalArpabetSpaceSep, Boolean keepSilence) {
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
        if (keepSilence != null) {
            body.add("keep_silence", textPart("keep_silence", keepSilence ? "true" : "false"));
        }
        TranscribeWire wire = execute("/transcribe", body, TranscribeWire.class);
        if (wire == null) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, "empty response from model server");
        }
        return toResult(wire, canonicalArpabetSpaceSep);
    }

    // perceived 를 표준 ARPABET 으로 정규화하고, canonical 토큰과 함께 mismatch inspector 에 흘려보낸다.
    private TranscribeResult toResult(TranscribeWire wire, String canonicalString) {
        PhonemeNormalizer.NormalizedPhonemes normalized =
                phonemeNormalizer.normalize(wire.perceived(), wire.peakSoftmax());
        List<String> normalizedPerceived = normalized.phonemes();
        List<Double> normalizedSoftmax = normalized.peakSoftmax();

        List<String> canonicalTokens = splitCanonical(canonicalString);
        // 비표준 음소가 perceived / canonical 어느 쪽에 있어도 한 줄 WARN — 사전 확장 단서.
        phonemeMismatchInspector.inspect(canonicalTokens, normalizedPerceived);

        return new TranscribeResult(
                normalizedPerceived,
                normalizedSoftmax,
                wire.durationSec(),
                wire.speechRate() == null ? SpeechRate.NORMAL : wire.speechRate(),
                wire.speechRateRatio(),
                wire.modelId(),
                wire.modelType());
    }

    private static List<String> splitCanonical(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            return List.of();
        }
        return List.of(canonical.trim().split("\\s+"));
    }

    // CMU 기반 baseline canonical 을 단어 단위로 받는다. 강세는 제거된 상태, 음소는 39 productive 인벤토리
    // 안의 코드 (모델 서버 책임). text 가 비면 빈 리스트를 돌려준다 — 호출 측이 generate 단축 분기에서 처리한다.
    public List<CanonicalWord> g2p(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String url = appProperties.modelServer().baseUrl() + "/g2p";
        G2pWire wire;
        try {
            wire = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(G2pWire.class);
        } catch (ResourceAccessException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, ex.getMessage());
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, ex.getResponseBodyAsString());
        }
        if (wire == null || wire.words() == null || wire.words().isEmpty()) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, "empty /g2p response");
        }
        return toCanonicalWords(wire.words());
    }

    // wire 항목을 CanonicalWord 로 옮긴다. word 가 비거나 phonemes 가 비어도 항목은 보존해
    // 호출 측 (LLM refine) 이 단어 위치 정보를 그대로 사용할 수 있게 한다.
    private static List<CanonicalWord> toCanonicalWords(List<G2pWord> wireWords) {
        List<CanonicalWord> out = new ArrayList<>(wireWords.size());
        for (G2pWord w : wireWords) {
            String word = w.word() == null ? "" : w.word();
            List<String> phonemes = w.phonemes() == null ? List.of() : List.copyOf(w.phonemes());
            out.add(new CanonicalWord(word, phonemes));
        }
        return out;
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

    // /transcribe 응답 wire 포맷. snake_case 및 camelCase 양쪽을 모두 받는다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranscribeWire(
            List<String> perceived,
            @JsonAlias("peak_softmax") List<Double> peakSoftmax,
            @JsonAlias("duration_sec") Double durationSec,
            @JsonAlias("speech_rate") SpeechRate speechRate,
            @JsonAlias("speech_rate_ratio") Double speechRateRatio,
            @JsonAlias("model_id") String modelId,
            @JsonAlias("model_type") String modelType
    ) {}

    // /g2p 응답 wire 포맷.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record G2pWire(List<G2pWord> words) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record G2pWord(String word, List<String> phonemes) {}
}
