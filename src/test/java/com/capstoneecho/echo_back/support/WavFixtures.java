package com.capstoneecho.echo_back.support;

/**
 * 컨트롤러/서비스 테스트에서 공유하는 작은 WAV 픽스처.
 *
 * <p>{@link com.capstoneecho.echo_back.pronunciation.recording.service.RecordingService#upload}
 * 가 RIFF 헤더만 검사하므로 헤더만 갖춘 최소 바이트로 충분하다.</p>
 */
public final class WavFixtures {

    public static final byte[] VALID_WAV =
            new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    public static final byte[] CORRUPT_AUDIO = new byte[] {'X', 'Y', 'Z', 'Z'};

    public static final byte[] EMPTY_AUDIO = new byte[0];

    private WavFixtures() {
    }
}
