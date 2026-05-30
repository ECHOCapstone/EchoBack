package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.TrackCreateRequest;
import com.capstoneecho.echo_back.admin.dto.TrackUpdateRequest;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 트랙 생성 / 수정 / 삭제. 조회는 TrackService 가 담당한다.
@Service
@Transactional
public class AdminTrackService {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;

    public AdminTrackService(TrackRepository trackRepository, ScriptRepository scriptRepository) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
    }

    public TrackSummaryResponse create(TrackCreateRequest request) {
        Track track = trackRepository.save(
                Track.create(request.title(), request.description(), request.displayOrder()));
        return TrackSummaryResponse.of(track, 0);
    }

    public TrackSummaryResponse update(Long trackId, TrackUpdateRequest request) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        track.update(request.title(), request.description(), request.displayOrder());
        int chapterCount = Math.toIntExact(
                scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(trackId));
        return TrackSummaryResponse.of(track, chapterCount);
    }

    // 스크립트가 연결돼 있으면 FK 무결성을 위해 삭제를 막는다 (먼저 스크립트를 옮기거나 지워야 한다).
    public void delete(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        if (scriptRepository.existsByTrack_Id(trackId)) {
            throw new BusinessException(ErrorCode.TRACK_HAS_SCRIPTS);
        }
        trackRepository.delete(track);
    }
}
