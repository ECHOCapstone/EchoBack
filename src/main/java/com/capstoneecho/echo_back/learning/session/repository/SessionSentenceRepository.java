package com.capstoneecho.echo_back.learning.session.repository;

import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSentenceRepository extends JpaRepository<SessionSentence, Long> {
}
