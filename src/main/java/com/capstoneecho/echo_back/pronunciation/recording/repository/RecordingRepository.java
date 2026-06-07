package com.capstoneecho.echo_back.pronunciation.recording.repository;

import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 한 사용자의 한 챕터에 속한 모든 녹음. step 별 latest 를 재구성하기 위해 step 까지 JOIN FETCH 한다.
    // 시간 순 ASC 로 정렬해 호출 측이 LinkedHashMap put 으로 자연스럽게 latest 만 남길 수 있다.
    @Query("""
            SELECT r FROM Recording r
              JOIN FETCH r.step s
             WHERE r.user.id = :userId AND r.script.id = :scriptId
             ORDER BY r.createdAt ASC
            """)
    List<Recording> findAllChapterRecordingsWithStep(
            @Param("userId") Long userId,
            @Param("scriptId") Long scriptId);

    // 한 사용자의 한 세션에 속한 모든 녹음. sentence 별 latest 를 재구성하기 위해 sentence 까지 JOIN FETCH 한다.
    @Query("""
            SELECT r FROM Recording r
              JOIN FETCH r.sessionSentence s
             WHERE r.user.id = :userId AND r.session.id = :sessionId
             ORDER BY r.createdAt ASC
            """)
    List<Recording> findAllSessionRecordingsWithSentence(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId);

    long countByUser_Id(Long userId);
}
