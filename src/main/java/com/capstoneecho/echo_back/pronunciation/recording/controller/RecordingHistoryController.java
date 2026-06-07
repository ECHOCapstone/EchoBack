package com.capstoneecho.echo_back.pronunciation.recording.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingHistoryResponse;
import com.capstoneecho.echo_back.pronunciation.recording.service.RecordingHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 본인이 이전에 끝낸 챕터 / 세션 시도의 압축 데이터를 돌려준다.
// FE 가 "이어서" 진입 시 호출해 채팅 화면 위쪽에 이전 카드들을 다시 그린다.
//   GET /api/scripts/{id}/recordings/history   — 챕터 학습용
//   GET /api/sessions/{id}/recordings/history  — 맞춤 세션용
@RestController
@RequestMapping("/api")
public class RecordingHistoryController {

    private final RecordingHistoryService recordingHistoryService;

    public RecordingHistoryController(RecordingHistoryService recordingHistoryService) {
        this.recordingHistoryService = recordingHistoryService;
    }

    @GetMapping("/scripts/{scriptId}/recordings/history")
    public ApiResponse<RecordingHistoryResponse> chapterHistory(
            @PathVariable Long scriptId,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(recordingHistoryService.forScript(principal.userId(), scriptId));
    }

    @GetMapping("/sessions/{sessionId}/recordings/history")
    public ApiResponse<RecordingHistoryResponse> sessionHistory(
            @PathVariable Long sessionId,
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(recordingHistoryService.forSession(principal.userId(), sessionId));
    }
}
