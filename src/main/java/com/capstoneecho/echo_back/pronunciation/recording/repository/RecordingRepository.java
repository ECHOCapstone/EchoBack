package com.capstoneecho.echo_back.pronunciation.recording.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByIdAndUserId(Long id, Long userId);

    List<Recording> findByUserIdAndIdIn(Long userId, List<Long> ids);
}
