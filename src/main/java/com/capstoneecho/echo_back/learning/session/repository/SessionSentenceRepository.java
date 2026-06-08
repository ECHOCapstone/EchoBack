package com.capstoneecho.echo_back.learning.session.repository;

import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSentenceRepository extends JpaRepository<SessionSentence, Long> {

    // CanonicalBootstrapper 페이지 단위 backfill — canonical 이 비어 있는 행만.
    // 맞춤학습은 사용자 즉석 콘텐츠가 많아 처음엔 누락 행이 다수일 수 있으나, lazy 도 받아주므로
    // 부팅 backfill 은 시드 / 운영자 정의 문장만 채우는 best-effort 보조이다.
    Page<SessionSentence> findByCanonicalCachedJsonIsNullOrderByIdAsc(Pageable pageable);
}
