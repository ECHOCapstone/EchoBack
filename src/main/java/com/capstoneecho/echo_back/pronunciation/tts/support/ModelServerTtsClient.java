package com.capstoneecho.echo_back.pronunciation.tts.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// app.tts.provider=model-server 일 때 활성화되는 TtsClient.
// ECHO_model 서버의 POST /tts (multipart: text, lang) 를 호출하고 audio/mpeg 바이트를 그대로 돌려준다.
@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "model-server")
public class ModelServerTtsClient implements TtsClient {

    private final RestClient restClient;
    private final String baseUrl;

    public ModelServerTtsClient(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        AppProperties.ModelServer ms = appProperties.modelServer();
        if (ms == null || ms.baseUrl() == null || ms.baseUrl().isBlank()) {
            throw new IllegalStateException("app.model-server.base-url must be configured");
        }
        this.baseUrl = ms.baseUrl();
    }

    @Override
    public byte[] synthesize(String text, Locale lang) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("lang", normalizeLang(lang));
        try {
            byte[] body = restClient.post()
                    .uri(baseUrl + "/tts")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR);
            }
            return body;
        } catch (ResourceAccessException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, ex.getMessage());
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, ex.getResponseBodyAsString());
        }
    }

    // gTTS 는 BCP-47 의 짧은 언어 코드 (예: en / ko) 만 받는다. forLanguageTag 의 빈 / null 입력은 en 으로 폴백.
    private static String normalizeLang(Locale lang) {
        if (lang == null) {
            return "en";
        }
        String tag = lang.getLanguage();
        return (tag == null || tag.isBlank()) ? "en" : tag;
    }
}
