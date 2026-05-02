package com.capstoneecho.echo_back.app.track;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.script.ScriptRepository;
import com.capstoneecho.echo_back.app.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.app.track.dto.TrackSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class TrackServiceImpl implements TrackService {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;

    TrackServiceImpl(TrackRepository trackRepository, ScriptRepository scriptRepository) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
    }

    @Override
    public List<TrackSummaryResponse> listAll() {
        return trackRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(track -> TrackSummaryResponse.of(
                        track,
                        scriptRepository.findByTrack_IdOrderByChapterOrderAscIdAsc(track.getId()).size()
                ))
                .toList();
    }

    @Override
    public TrackDetailResponse getDetail(Long trackId) {
        var track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        var chapters = scriptRepository.findByTrack_IdOrderByChapterOrderAscIdAsc(trackId);
        return TrackDetailResponse.of(track, chapters);
    }
}
