package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.common.ApiResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.app.jwt.CurrentUser;
import com.capstoneecho.echo_back.app.jwt.JwtPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 피드백 도메인의 조회 전용 엔드포인트. 쓰기 흐름과 책임을 나눠 두기 위해 컨트롤러를 분리한다.
// 경로(/api/feedbacks 복수형) 는 프론트가 이미 의존하므로 보존한다.
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbacksReadController {

    private final FeedbackService feedbackService;

    public FeedbacksReadController(FeedbackService feedbackService) {
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
