package com.capstoneecho.echo_back.learning.track.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
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
        // 카운트 전용 쿼리로 트랙별 챕터 수만 가져온다 (목록 화면은 챕터 본문이 필요 없음).
        return trackRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(track -> TrackSummaryResponse.of(
                        track,
                        (int) scriptRepository.countByTrack_Id(track.getId())
                ))
                .toList();
    }

    @Override
    public TrackDetailResponse getDetail(Long trackId) {
        var track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        var chapters = scriptRepository.findByTrack_IdOrderByChapterOrderAsc(trackId);
        return TrackDetailResponse.of(track, chapters);
    }
}
