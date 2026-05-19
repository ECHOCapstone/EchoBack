package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// 업로드된 오디오 multipart 한 건에서 빈/손상 케이스를 일관된 BusinessException 으로 매핑하면서
// 바이트 배열을 꺼내 준다. 녹음 업로드와 재연습 평가가 같은 검증·디코딩 규칙을 공유하도록 한 곳에 둔다.
@Component
public class MultipartAudioReader {

    public byte[] read(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "audio 파일이 비어 있습니다.");
        }
        try {
            return audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, e.getMessage());
        }
    }
}
