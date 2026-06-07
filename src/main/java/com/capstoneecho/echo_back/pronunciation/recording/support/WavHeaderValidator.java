package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Component;

// WAV 파일의 헤더 무결성 검증. 본격 디코딩과 음향 처리는 모델 서버가 담당하지만, 잘못된 형식이
// 모델 서버까지 전달되면 "MODEL_SERVER_ERROR" 라는 모호한 에러로 사용자에게 노출되므로
// 형식 위반은 이 단계에서 명확한 안내 (AUDIO_DECODE_FAILED) 로 끊는다.
//
// 검증 범위:
//   RIFF 컨테이너 magic + WAVE 식별자 + fmt 청크 존재 + 채널/비트 깊이 합리성.
@Component
public class WavHeaderValidator {

    // RIFF 헤더 0-3 "RIFF", 4-7 size, 8-11 "WAVE" 로 최소 12 bytes.
    // fmt 청크는 추가로 8 bytes 의 chunk 헤더 + 16 bytes 이상의 본문이 와야 한다.
    private static final int MIN_WAV_BYTES = 44;

    // PCM (1) / IEEE float (3) 두 일반 포맷만 허용. 압축 포맷은 모델 서버가 받지 못한다.
    private static final int FMT_PCM = 1;
    private static final int FMT_IEEE_FLOAT = 3;

    public void require(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < MIN_WAV_BYTES) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        if (audioBytes[0] != 'R' || audioBytes[1] != 'I'
                || audioBytes[2] != 'F' || audioBytes[3] != 'F'
                || audioBytes[8] != 'W' || audioBytes[9] != 'A'
                || audioBytes[10] != 'V' || audioBytes[11] != 'E') {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        // 가장 앞 청크가 "fmt " 인지만 확인한다 (스트림이 "fmt " 가 아닌 "JUNK" 로 시작하는 변종은
        // 일반 마이크 녹음에는 나타나지 않는다). 본문에서 채널 수와 비트 깊이를 끄집어 합리성 검사.
        if (audioBytes[12] != 'f' || audioBytes[13] != 'm'
                || audioBytes[14] != 't' || audioBytes[15] != ' ') {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        int audioFormat = readLeUInt16(audioBytes, 20);
        int channels = readLeUInt16(audioBytes, 22);
        int bitsPerSample = readLeUInt16(audioBytes, 34);
        if (audioFormat != FMT_PCM && audioFormat != FMT_IEEE_FLOAT) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        if (channels < 1 || channels > 2) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED);
        }
    }

    // little-endian uint16 디코딩. WAV 헤더 정수 필드의 표준 인코딩.
    private static int readLeUInt16(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }
}
