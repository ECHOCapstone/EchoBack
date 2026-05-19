package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelAnalyzeResponse;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelG2pResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 모델 서버 호출의 단일 지점. /analyze, /g2p 의 multipart/form-data 인코딩과 예외 매핑을 담당한다.
//
// Spring 표준 패턴: MultipartBodyBuilder 로 파트를 구성하고 RestClient.body(builder.build()) 로 전달.
// 명시적 contentType(MULTIPART_FORM_DATA) 와 ByteArrayResource 의 익명 서브클래스로 노출되는
// getFilename() 이 FastAPI UploadFile 인식의 전제 조건이다.
@Component
class RestClientModelServerClient implements ModelServerClient {

    private static final String ANALYZE_ENDPOINT = "/analyze";
    private static final String G2P_ENDPOINT = "/g2p";
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

        return postMultipart(ANALYZE_ENDPOINT, builder, ModelAnalyzeResponse.class);
    }

    @Override
    public String g2p(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var builder = new MultipartBodyBuilder();
        builder.part("text", text);
        var response = postMultipart(G2P_ENDPOINT, builder, ModelG2pResponse.class);
        return response != null && response.phonemes() != null ? response.phonemes() : "";
    }

    // 모델 서버 호출의 공통 처리 - 표준 multipart 인코딩과 ResourceAccess/RestClientResponse 예외 매핑.
    private <T> T postMultipart(String endpoint, MultipartBodyBuilder builder, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(responseType);
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
