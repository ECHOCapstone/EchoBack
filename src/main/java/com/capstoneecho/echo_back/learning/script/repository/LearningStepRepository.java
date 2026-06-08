package com.capstoneecho.echo_back.learning.script.repository;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    List<LearningStep> findByScript_IdOrderByIdAsc(Long scriptId);

    // 스크립트 수정/삭제 시 기존 스텝을 모두 제거하기 위한 일괄 삭제.
    long deleteByScript_Id(Long scriptId);

    // CanonicalBootstrapper 페이지 단위 backfill — RECORD kind 중 canonical 이 비어 있는 행만.
    Page<LearningStep> findByKindAndCanonicalCachedJsonIsNullOrderByIdAsc(
            StepKind kind, Pageable pageable);
}
