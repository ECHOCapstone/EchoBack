package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Component;

// WAV 파일의 최소 무결성 (RIFF/WAVE magic) 검증. 본격 디코딩은 모델 서버가 한다.
@Component
public class WavHeaderValidator {

    // RIFF 헤더는 바이트 0-3 "RIFF", 4-7 size, 8-11 "WAVE" 로 최소 12 bytes 다.
    private static final int MIN_WAV_BYTES = 12;

    public void require(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < MIN_WAV_BYTES) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        // 바이트 0-3 = "RIFF", 8-11 = "WAVE" 두 magic 을 모두 확인한다.
        if (audioBytes[0] != 'R' || audioBytes[1] != 'I'
                || audioBytes[2] != 'F' || audioBytes[3] != 'F'
                || audioBytes[8] != 'W' || audioBytes[9] != 'A'
                || audioBytes[10] != 'V' || audioBytes[11] != 'E') {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
    }
}
