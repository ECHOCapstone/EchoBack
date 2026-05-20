package com.capstoneecho.echo_back.pronunciation.feedback.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackGenerateRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResult;
import com.capstoneecho.echo_back.pronunciation.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/generate")
    public ApiResponse<FeedbackDetailResponse> generate(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody FeedbackGenerateRequest request) {
        return ApiResponse.success(feedbackService.generate(principal.userId(), request));
    }

    @PostMapping(value = "/{feedbackId}/retry-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RetryWordResult> retryWord(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId,
            @RequestParam("audio") MultipartFile audio) {
        byte[] audioBytes = readAudioBytes(audio);
        return ApiResponse.success(
                feedbackService.retryWord(principal.userId(), feedbackId, audioBytes));
    }

    @PostMapping("/{feedbackId}/complete")
    public ApiResponse<UserResponse> complete(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId) {
        return ApiResponse.success(feedbackService.complete(principal.userId(), feedbackId));
    }

    private static byte[] readAudioBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, ex.getMessage());
        }
    }
}
