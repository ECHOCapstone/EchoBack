package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Component;

// WAV 파일의 최소 무결성 (RIFF magic + 최소 헤더 길이) 검증. 본격 디코딩은 모델 서버가 한다.
@Component
public class WavHeaderValidator {

    // RIFF 헤더 (`RIFF` magic + size + `WAVE` magic) 최소 8 bytes + 우리가 추가로 요구하는 여유.
    private static final int MIN_WAV_BYTES = 12;
    private static final byte R = 'R';
    private static final byte I = 'I';
    private static final byte F = 'F';

    public void require(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < MIN_WAV_BYTES) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        if (audioBytes[0] != R || audioBytes[1] != I
                || audioBytes[2] != F || audioBytes[3] != F) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
    }
}
