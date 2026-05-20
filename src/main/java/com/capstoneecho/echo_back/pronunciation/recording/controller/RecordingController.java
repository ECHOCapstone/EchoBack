package com.capstoneecho.echo_back.pronunciation.recording.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.service.RecordingService;
import com.capstoneecho.echo_back.pronunciation.recording.support.AudioBytesReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RecordingUploadResponse>> upload(
            @CurrentUser JwtPrincipal principal,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "scriptId", required = false) Long scriptId,
            @RequestParam(value = "stepId", required = false) Long stepId,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "sessionSentenceId", required = false) Long sessionSentenceId) {
        byte[] audioBytes = AudioBytesReader.read(audio);
        RecordingUploadRequest request = new RecordingUploadRequest(
                scriptId, stepId, sessionId, sessionSentenceId);
        RecordingUploadResponse response =
                recordingService.upload(principal.userId(), request, audioBytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
