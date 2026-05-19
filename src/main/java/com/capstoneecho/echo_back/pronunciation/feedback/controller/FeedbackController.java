package com.capstoneecho.echo_back.pronunciation.feedback.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.capstoneecho.echo_back.pronunciation.feedback.service.FeedbackService;
// 피드백을 새로 만들거나 보상으로 마무리하는 쓰기 계열 엔드포인트.
// 동일 도메인의 조회는 FeedbacksReadController 가 /api/feedbacks 경로로 분리해서 담당한다.
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/generate")
    public ApiResponse<FeedbackResponse> generate(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody GenerateFeedbackRequest request
    ) {
        return ApiResponse.ok(feedbackService.generate(principal.userId(), request));
    }

    @PostMapping(value = "/{feedbackId}/retry-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RetryWordResponse> retryWord(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId,
            @RequestPart("audio") MultipartFile audio
    ) {
        return ApiResponse.ok(feedbackService.retryWord(principal.userId(), feedbackId, audio));
    }

    @PostMapping("/{feedbackId}/complete")
    public ApiResponse<UserResponse> complete(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId
    ) {
        return ApiResponse.ok(feedbackService.complete(principal.userId(), feedbackId));
    }
}
