package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.app.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.app.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.app.feedback.dto.RetryWordResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FeedbackService {

    FeedbackResponse generate(Long userId, GenerateFeedbackRequest request);

    RetryWordResponse retryWord(Long userId, Long feedbackId, MultipartFile audio);

    List<FeedbackSummaryResponse> listMine(Long userId);

    FeedbackResponse get(Long userId, Long feedbackId);
}
