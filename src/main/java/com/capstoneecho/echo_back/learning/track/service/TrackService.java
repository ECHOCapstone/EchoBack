package com.capstoneecho.echo_back.learning.track.service;

import com.capstoneecho.echo_back.learning.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;

import java.util.List;

public interface TrackService {

    List<TrackSummaryResponse> listAll();

    TrackDetailResponse getDetail(Long trackId);
}
