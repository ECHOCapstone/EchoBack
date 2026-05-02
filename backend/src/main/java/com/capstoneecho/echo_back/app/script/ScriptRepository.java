package com.capstoneecho.echo_back.app.script;

import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<Script, Long> {

    List<Script> findByIsPresetTrueOrderByIdAsc();
}
