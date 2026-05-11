package com.capstoneecho.echo_back.learning.script.repository;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<Script, Long> {

    List<Script> findByTrack_IdOrderByChapterOrderAsc(Long trackId);

    List<Script> findByPresetTrueOrderByIdAsc();
}
