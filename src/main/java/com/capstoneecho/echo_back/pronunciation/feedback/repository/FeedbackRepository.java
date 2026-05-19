package com.capstoneecho.echo_back.pronunciation.feedback.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
public interface FeedbackRepository extends JpaRepository<PronunciationFeedback, Long> {

    Optional<PronunciationFeedback> findByIdAndUserId(Long id, Long userId);

    List<PronunciationFeedback> findByUserIdOrderByCreatedAtDesc(Long userId);
}
