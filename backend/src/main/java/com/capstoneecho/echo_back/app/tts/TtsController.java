package com.capstoneecho.echo_back.app.tts;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 모델 서버 /tts 프록시. Spring 표준 form-urlencoded 변환을 사용한다.
//   - body 는 MultiValueMap<String, String>
//   - contentType 은 APPLICATION_FORM_URLENCODED 를 명시 (FormHttpMessageConverter 가 인코딩)
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final String DEFAULT_LANG = "en";

    private final RestClient modelRestClient;

    public TtsController(RestClient modelRestClient) {
        this.modelRestClient = modelRestClient;
    }

    @PostMapping
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request) {
        var lang = request.lang() != null && !request.lang().isBlank() ? request.lang() : DEFAULT_LANG;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", request.text());
        form.add("lang", lang);

        try {
            var bytes = modelRestClient.post()
                    .uri("/tts")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(bytes);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, e.getMessage());
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, e.getResponseBodyAsString());
        }
    }
}
