package com.capstoneecho.echo_back.learning.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    // LearningStep 은 시드 단계에서 orderIndex 순으로 적재되므로 PK 순과 일치한다.
    // 안정적 출력 순서를 위해 PK 기준으로 조회한다.
    List<LearningStep> findByScript_IdOrderByIdAsc(Long scriptId);

    Optional<LearningStep> findByIdAndScript_Id(Long id, Long scriptId);
}
