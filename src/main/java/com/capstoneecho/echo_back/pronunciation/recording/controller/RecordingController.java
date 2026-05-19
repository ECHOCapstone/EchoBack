package com.capstoneecho.echo_back.pronunciation.recording.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.capstoneecho.echo_back.pronunciation.recording.service.RecordingService;
@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecordingResponse> upload(
            @CurrentUser JwtPrincipal principal,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "scriptId", required = false) Long scriptId,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "stepId", required = false) Long stepId,
            @RequestParam(value = "sessionSentenceId", required = false) Long sessionSentenceId
    ) {
        return ApiResponse.success(recordingService.upload(
                principal.userId(), scriptId, sessionId, stepId, sessionSentenceId, audio
        ));
    }
}
