package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingResponse;
import org.springframework.web.multipart.MultipartFile;

import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
public interface RecordingService {

    RecordingResponse upload(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId,
            MultipartFile audio
    );

    Recording getEntity(Long userId, Long recordingId);
}
