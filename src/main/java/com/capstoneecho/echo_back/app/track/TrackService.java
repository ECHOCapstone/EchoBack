package com.capstoneecho.echo_back.app.track;

import com.capstoneecho.echo_back.app.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.app.track.dto.TrackSummaryResponse;

import java.util.List;

public interface TrackService {

    List<TrackSummaryResponse> listAll();

    TrackDetailResponse getDetail(Long trackId);
}
