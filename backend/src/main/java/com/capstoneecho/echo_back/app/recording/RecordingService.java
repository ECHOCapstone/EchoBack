package com.capstoneecho.echo_back.app.recording;

import com.capstoneecho.echo_back.app.recording.dto.RecordingResponse;
import org.springframework.web.multipart.MultipartFile;

public interface RecordingService {

    RecordingResponse upload(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            MultipartFile audio
    );

    Recording getEntity(Long userId, Long recordingId);
}
