package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.common.ApiResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.app.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.app.feedback.dto.RetryWordResponse;
import com.capstoneecho.echo_back.app.jwt.CurrentUser;
import com.capstoneecho.echo_back.app.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.app.member.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

@RestController
@RequestMapping("/api/feedbacks")
class FeedbacksReadController {

    private final FeedbackService feedbackService;

    FeedbacksReadController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<List<FeedbackSummaryResponse>> list(@CurrentUser JwtPrincipal principal) {
        return ApiResponse.ok(feedbackService.listMine(principal.userId()));
    }

    @GetMapping("/{feedbackId}")
    public ApiResponse<FeedbackResponse> get(
            @CurrentUser JwtPrincipal principal,
            @PathVariable Long feedbackId
    ) {
        return ApiResponse.ok(feedbackService.get(principal.userId(), feedbackId));
    }
}
