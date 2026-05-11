package com.capstoneecho.echo_back.pronunciation.tts.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.pronunciation.tts.dto.TtsRequest;
import com.capstoneecho.echo_back.pronunciation.tts.support.TtsClient;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TtsService {

    private final TtsClient ttsClient;

    public TtsService(TtsClient ttsClient) {
        this.ttsClient = ttsClient;
    }

    public byte[] synthesize(TtsRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "text 는 비어 있을 수 없습니다.");
        }
        Locale locale = parseLocale(request.locale());
        return ttsClient.synthesize(request.text(), locale);
    }

    private static Locale parseLocale(String tag) {
        if (tag == null || tag.isBlank()) {
            return Locale.US;
        }
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.US;
        }
    }
}
