package com.capstoneecho.echo_back.pronunciation.tts.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstoneecho.echo_back.pronunciation.tts.dto.TtsRequest;
import com.capstoneecho.echo_back.pronunciation.tts.service.TtsService;
// 클라이언트가 발음 학습 흐름에서 사용할 영어 합성 음성을 받기 위한 진입점.
// 외부 모델 서버 호출은 TtsService 구현체가 책임지고, 컨트롤러는 HTTP 계층 변환만 담당한다.
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request) {
        var audio = ttsService.synthesize(request.text(), request.lang());
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .body(audio);
    }
}
