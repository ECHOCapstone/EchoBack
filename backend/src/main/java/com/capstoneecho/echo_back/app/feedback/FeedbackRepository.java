package com.capstoneecho.echo_back.app.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<PronunciationFeedback, Long> {

    Optional<PronunciationFeedback> findByIdAndUserId(Long id, Long userId);

    List<PronunciationFeedback> findByUserIdOrderByCreatedAtDesc(Long userId);
}
