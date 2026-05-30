package com.capstoneecho.echo_back.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.admin.dto.TrackCreateRequest;
import com.capstoneecho.echo_back.admin.dto.TrackUpdateRequest;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminTrackServiceTest {

    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final ScriptRepository scriptRepository = mock(ScriptRepository.class);
    private final AdminTrackService service = new AdminTrackService(trackRepository, scriptRepository);

    @Test
    @DisplayName("create 는 트랙을 저장하고 요약을 돌려준다")
    void createSavesTrack() {
        when(trackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrackSummaryResponse r = service.create(new TrackCreateRequest("R 발음", "설명", 1));

        assertThat(r.title()).isEqualTo("R 발음");
        assertThat(r.chapterCount()).isZero();
    }

    @Test
    @DisplayName("update 는 트랙을 갱신하고 챕터 수를 반영한다")
    void updateMutatesTrack() {
        Track track = Track.create("Old", "d", 1);
        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(5L)).thenReturn(3L);

        TrackSummaryResponse r = service.update(5L, new TrackUpdateRequest("New", "d2", 2));

        assertThat(track.getTitle()).isEqualTo("New");
        assertThat(track.getDisplayOrder()).isEqualTo(2);
        assertThat(r.chapterCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("없는 트랙 update 는 TRACK_NOT_FOUND")
    void updateMissingThrows() {
        when(trackRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new TrackUpdateRequest("X", null, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("스크립트가 연결된 트랙 삭제는 막는다")
    void deleteRejectedWhenHasScripts() {
        Track track = Track.create("T", "d", 1);
        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(scriptRepository.existsByTrack_Id(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(BusinessException.class);
        verify(trackRepository, never()).delete(any());
    }

    @Test
    @DisplayName("빈 트랙은 삭제된다")
    void deleteEmptyTrack() {
        Track track = Track.create("T", "d", 1);
        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(scriptRepository.existsByTrack_Id(5L)).thenReturn(false);

        service.delete(5L);

        verify(trackRepository).delete(track);
    }
}
