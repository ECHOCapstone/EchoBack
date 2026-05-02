package com.capstoneecho.echo_back.app.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    List<LearningStep> findByScript_IdOrderByOrderIndexAsc(Long scriptId);

    Optional<LearningStep> findByIdAndScript_Id(Long id, Long scriptId);
}
