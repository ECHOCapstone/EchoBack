package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
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

    public ModelServerClient(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
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
        AnalyzeWire wire = execute("/analyze", body, AnalyzeWire.class);
        return toResult(wire);
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
                Optional.ofNullable(wire.canonical()),
                wire.peakSoftmax(),
                wire.alignment(),
                wire.errors(),
                Optional.ofNullable(wire.per()),
                wire.durationSec()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalyzeWire(
            List<String> perceived,
            List<String> canonical,
            List<Double> peakSoftmax,
            List<Object> alignment,
            List<AnalyzeError> errors,
            Double per,
            double durationSec
    ) {}
}
