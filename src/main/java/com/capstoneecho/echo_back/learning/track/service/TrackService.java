package com.capstoneecho.echo_back.learning.track.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.dto.ChapterResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrackService {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;

    public TrackService(TrackRepository trackRepository, ScriptRepository scriptRepository) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
    }

    public List<TrackSummaryResponse> listTracks() {
        return trackRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(TrackSummaryResponse::from)
                .toList();
    }

    public TrackDetailResponse getTrack(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));

        List<ChapterResponse> chapters = scriptRepository
                .findByTrack_IdOrderByChapterOrderAsc(trackId).stream()
                .filter(this::hasChapterOrder)
                .map(ChapterResponse::from)
                .toList();

        return TrackDetailResponse.of(track, chapters);
    }

    private boolean hasChapterOrder(Script script) {
        return script.getChapterOrder() != null;
    }
}
