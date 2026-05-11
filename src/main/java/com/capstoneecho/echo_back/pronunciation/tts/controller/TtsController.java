package com.capstoneecho.echo_back.pronunciation.tts.controller;

import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.pronunciation.tts.dto.TtsRequest;
import com.capstoneecho.echo_back.pronunciation.tts.service.TtsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping
    public ResponseEntity<byte[]> synthesize(
            @CurrentUser JwtPrincipal principal,
            @RequestBody TtsRequest request) {
        byte[] mp3 = ttsService.synthesize(request);
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .contentLength(mp3.length)
                .body(mp3);
    }
}
