package com.capstoneecho.echo_back.app.recording;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByIdAndUserId(Long id, Long userId);

    List<Recording> findByUserIdAndIdIn(Long userId, List<Long> ids);
}
