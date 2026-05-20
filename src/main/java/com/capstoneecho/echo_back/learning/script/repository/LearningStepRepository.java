package com.capstoneecho.echo_back.learning.script.repository;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    List<LearningStep> findByScript_IdOrderByIdAsc(Long scriptId);
}
