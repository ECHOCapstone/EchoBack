package com.capstoneecho.echo_back.pronunciation.feedback.repository;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.RetryAttempt;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetryAttemptRepository extends JpaRepository<RetryAttempt, Long> {

    // 한 feedback 의 최신 retry 시도들. 호출 측이 Pageable 로 cap 을 강제한다.
    List<RetryAttempt> findByFeedback_IdOrderByCreatedAtDesc(Long feedbackId, Pageable pageable);
}
