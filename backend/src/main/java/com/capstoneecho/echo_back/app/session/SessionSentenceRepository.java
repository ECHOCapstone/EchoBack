package com.capstoneecho.echo_back.app.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionSentenceRepository extends JpaRepository<SessionSentence, Long> {

    // 한 문장에 대한 녹음 업로드 시 sentenceId 가 현재 사용자 소유 세션에 속하는지
    // 한 번에 검증하기 위한 조회.
    Optional<SessionSentence> findByIdAndSession_UserId(Long id, Long userId);
}
