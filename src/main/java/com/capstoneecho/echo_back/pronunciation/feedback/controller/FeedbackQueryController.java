package com.capstoneecho.echo_back.pronunciation.feedback.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackDetailResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.service.FeedbackService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackQueryController {

    private final FeedbackService feedbackService;

    public FeedbackQueryController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<List<FeedbackSummaryResponse>> list(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(feedbackService.list(principal.userId()));
    }

    @GetMapping("/{feedbackId}")
    public ApiResponse<FeedbackDetailResponse> get(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId) {
        return ApiResponse.success(feedbackService.get(principal.userId(), feedbackId));
    }
}
