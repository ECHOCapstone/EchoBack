package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 모델 서버 /analyze 호출 단일 지점.
//
// Spring 표준 패턴: MultipartBodyBuilder 로 파트를 구성하고 RestClient.body(builder.build()) 로 전달.
// 명시적 contentType(MULTIPART_FORM_DATA) 와 ByteArrayResource 의 익명 서브클래스로 노출되는
// getFilename() 이 FastAPI UploadFile 인식의 전제 조건이다.
@Component
class RestClientModelServerClient implements ModelServerClient {

    private static final String ENDPOINT = "/analyze";
    private static final String DEFAULT_FILENAME = "audio.wav";
    private static final String DEFAULT_AUDIO_TYPE = "audio/wav";

    private final RestClient restClient;

    RestClientModelServerClient(RestClient modelRestClient) {
        this.restClient = modelRestClient;
    }

    @Override
    public ModelAnalyzeResponse analyze(byte[] audio, String filename, String contentType, String canonical) {
        var safeFilename = filename != null && !filename.isBlank() ? filename : DEFAULT_FILENAME;
        var safeContentType = contentType != null && !contentType.isBlank() ? contentType : DEFAULT_AUDIO_TYPE;

        var builder = new MultipartBodyBuilder();
        builder.part("audio", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return safeFilename;
            }
        }).contentType(parseMediaType(safeContentType));
        if (canonical != null && !canonical.isBlank()) {
            builder.part("canonical", canonical);
        }

        try {
            return restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(ModelAnalyzeResponse.class);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, e.getMessage());
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.MODEL_SERVER_ERROR, e.getResponseBodyAsString());
        }
    }

    private MediaType parseMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
