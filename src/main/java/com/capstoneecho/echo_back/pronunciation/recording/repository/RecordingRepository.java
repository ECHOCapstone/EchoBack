package com.capstoneecho.echo_back.pronunciation.recording.repository;

import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByIdAndUser_Id(Long id, Long userId);

    List<Recording> findAllByUser_IdAndScript_IdAndStep_IdOrderByCreatedAtAsc(
            Long userId, Long scriptId, Long stepId);

    List<Recording> findAllByUser_IdAndSession_IdAndSessionSentence_IdOrderByCreatedAtAsc(
            Long userId, Long sessionId, Long sessionSentenceId);

    List<Recording> findAllByUser_IdAndScript_IdAndIdInOrderByCreatedAtAsc(
            Long userId, Long scriptId, Collection<Long> ids);

    List<Recording> findAllByUser_IdAndSession_IdAndIdInOrderByCreatedAtAsc(
            Long userId, Long sessionId, Collection<Long> ids);
}
