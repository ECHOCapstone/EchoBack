package com.capstoneecho.echo_back.learning.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {

    List<LearningStep> findByScript_IdOrderByOrderIndexAsc(Long scriptId);

    Optional<LearningStep> findByIdAndScript_Id(Long id, Long scriptId);
}
