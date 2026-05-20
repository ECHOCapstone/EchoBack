package com.capstoneecho.echo_back.pronunciation.tts.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.pronunciation.tts.dto.TtsRequest;
import com.capstoneecho.echo_back.pronunciation.tts.support.TtsClient;
import java.util.Locale;
import org.springframework.stereotype.Service;

// TTS 합성 진입점. 본 합성은 TtsClient 구현체에 위임하고, 여기서는 검증 / 언어 태그 정규화만 한다.
@Service
public class TtsService {

    private final TtsClient ttsClient;
    private final String textRequiredMessage;

    public TtsService(TtsClient ttsClient, AppProperties appProperties) {
        this.ttsClient = ttsClient;
        AppProperties.Messages m = appProperties.messages();
        this.textRequiredMessage = m == null ? "" : m.ttsTextRequired();
    }

    public byte[] synthesize(TtsRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, textRequiredMessage);
        }
        Locale lang = parseLang(request.lang());
        return ttsClient.synthesize(request.text(), lang);
    }

    // 잘못된 BCP-47 태그를 받아도 예외가 사용자에게 전파되지 않게 en-US 로 폴백한다.
    private static Locale parseLang(String tag) {
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
