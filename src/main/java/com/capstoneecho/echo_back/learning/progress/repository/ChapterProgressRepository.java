package com.capstoneecho.echo_back.learning.progress.repository;

import com.capstoneecho.echo_back.learning.progress.entity.ChapterProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterProgressRepository extends JpaRepository<ChapterProgress, Long> {

    Optional<ChapterProgress> findByUser_IdAndScript_Id(Long userId, Long scriptId);

    void deleteByUser_IdAndScript_Id(Long userId, Long scriptId);
}
