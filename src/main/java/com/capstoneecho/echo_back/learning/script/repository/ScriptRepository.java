package com.capstoneecho.echo_back.learning.script.repository;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScriptRepository extends JpaRepository<Script, Long> {

    List<Script> findByTrack_IdOrderByChapterOrderAsc(Long trackId);

    List<Script> findByPresetTrueOrderByIdAsc();

    // 트랙 삭제 가드 — 연결된 스크립트가 하나라도 있으면 삭제를 막는다.
    boolean existsByTrack_Id(Long trackId);

    // 트랙의 챕터(chapterOrder 가 있는 스크립트) 수.
    long countByTrack_IdAndChapterOrderIsNotNull(Long trackId);

    // 트랙별 챕터(=chapterOrder 가 있는 스크립트) 수를 한 번의 GROUP BY 로 집계한다 (N+1 회피).
    @Query("""
            SELECT s.track.id AS trackId, COUNT(s) AS chapterCount
              FROM Script s
             WHERE s.chapterOrder IS NOT NULL
             GROUP BY s.track.id
            """)
    List<TrackChapterCount> countChaptersGroupedByTrack();

    interface TrackChapterCount {
        Long getTrackId();

        long getChapterCount();
    }
}
