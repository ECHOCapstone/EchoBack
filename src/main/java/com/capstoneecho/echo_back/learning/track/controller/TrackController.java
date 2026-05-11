package com.capstoneecho.echo_back.learning.track.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackDetailResponse;
import com.capstoneecho.echo_back.learning.track.dto.TrackSummaryResponse;
import com.capstoneecho.echo_back.learning.track.service.TrackService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping
    public ApiResponse<List<TrackSummaryResponse>> list() {
        return ApiResponse.success(trackService.listTracks());
    }

    @GetMapping("/{trackId}")
    public ApiResponse<TrackDetailResponse> get(@PathVariable Long trackId) {
        return ApiResponse.success(trackService.getTrack(trackId));
    }
}
