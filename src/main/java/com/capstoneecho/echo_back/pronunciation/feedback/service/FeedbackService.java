package com.capstoneecho.echo_back.pronunciation.feedback.service;

import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.FeedbackSummaryResponse;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.GenerateFeedbackRequest;
import com.capstoneecho.echo_back.pronunciation.feedback.dto.RetryWordResponse;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FeedbackService {

    FeedbackResponse generate(Long userId, GenerateFeedbackRequest request);

    RetryWordResponse retryWord(Long userId, Long feedbackId, MultipartFile audio);

    List<FeedbackSummaryResponse> listMine(Long userId);

    FeedbackResponse get(Long userId, Long feedbackId);

    // 한 챕터 학습이 모두 끝났음을 백엔드에 알리고 EXP/streak 보상을 적용한다.
    // 응답으로 갱신된 사용자 정보를 돌려주어 프론트가 즉시 헤더 표시를 갱신할 수 있게 한다.
    UserResponse complete(Long userId, Long feedbackId);
}
