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
        // 카운트 전용 쿼리로 트랙별 챕터 수만 가져온다 (목록 화면은 챕터 본문이 필요 없음).
        return trackRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
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
        var chapters = scriptRepository.findByTrack_IdOrderByChapterOrderAscIdAsc(trackId);
        return TrackDetailResponse.of(track, chapters);
    }
}
