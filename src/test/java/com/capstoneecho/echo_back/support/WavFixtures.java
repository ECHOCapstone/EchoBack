package com.capstoneecho.echo_back.support;

// 컨트롤러/서비스 테스트에서 공유하는 최소 유효 WAV 픽스처.
// WavHeaderValidator 가 44 바이트 RIFF/WAVE + "fmt " + PCM + 1ch + 16-bit 까지 검증하므로
// 그 모든 칸을 채운 44 바이트 헤더를 만든다 (data 청크 본문은 0 바이트).
public final class WavFixtures {

    public static final int SAMPLE_RATE = 16000;
    public static final int NUM_CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;

    // 표준 RIFF/WAVE 44-byte 헤더 (PCM, 16000 Hz, mono, 16-bit, data 청크 = 0 bytes).
    public static final byte[] VALID_WAV = buildHeader();

    public static final byte[] CORRUPT_AUDIO = new byte[] {'X', 'Y', 'Z', 'Z'};

    public static final byte[] EMPTY_AUDIO = new byte[0];

    private WavFixtures() {
    }

    private static byte[] buildHeader() {
        int byteRate = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = NUM_CHANNELS * BITS_PER_SAMPLE / 8;
        int dataSize = 0;
        int riffSize = 36 + dataSize;

        byte[] h = new byte[44];
        // RIFF header
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        putLeUInt32(h, 4, riffSize);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        // fmt chunk
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        putLeUInt32(h, 16, 16);              // subchunk1 size
        putLeUInt16(h, 20, 1);               // audio format = PCM
        putLeUInt16(h, 22, NUM_CHANNELS);
        putLeUInt32(h, 24, SAMPLE_RATE);
        putLeUInt32(h, 28, byteRate);
        putLeUInt16(h, 32, blockAlign);
        putLeUInt16(h, 34, BITS_PER_SAMPLE);
        // data chunk header
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        putLeUInt32(h, 40, dataSize);
        return h;
    }

    private static void putLeUInt16(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putLeUInt32(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
