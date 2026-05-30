package com.capstoneecho.echo_back.learning.script.repository;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    List<LearningStep> findByScript_IdOrderByIdAsc(Long scriptId);

    // 스크립트 수정/삭제 시 기존 스텝을 모두 제거하기 위한 일괄 삭제.
    long deleteByScript_Id(Long scriptId);
}
