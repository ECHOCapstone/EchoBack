package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

// 멀티파트 오디오 파일을 byte[] 로 읽어내는 공용 헬퍼. I/O 실패는 AUDIO_DECODE_FAILED 로 변환한다.
public final class AudioBytesReader {

    private AudioBytesReader() {}

    public static byte[] read(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.AUDIO_DECODE_FAILED, ex.getMessage());
        }
    }
}
