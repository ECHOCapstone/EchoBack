package com.capstoneecho.echo_back.pronunciation.tts.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.capstoneecho.echo_back.pronunciation.tts.service.TtsService;
// 모델 서버 /tts 로 form-urlencoded 요청을 보내 합성된 음성 바이트를 받아온다.
@Service
class ModelServerTtsService implements TtsService {

    private static final String ENDPOINT = "/tts";
    private static final String DEFAULT_LANG = "en";

    private final RestClient modelRestClient;

    ModelServerTtsService(RestClient modelRestClient) {
        this.modelRestClient = modelRestClient;
    }

    @Override
    public byte[] synthesize(String text, String lang) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("lang", lang != null && !lang.isBlank() ? lang : DEFAULT_LANG);

        try {
            return modelRestClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, e.getMessage());
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, e.getResponseBodyAsString());
        }
    }
}
