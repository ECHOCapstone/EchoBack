package com.capstoneecho.echo_back.pronunciation.tts.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 로컬 스텁 TTS 구현. ID3v2 헤더 magic (0x49 0x44 0x33) 으로 시작하는
// 고정 길이 (~100 bytes) non-empty MP3 페이로드를 돌려준다.
@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "local-stub", matchIfMissing = true)
public class DefaultTtsClient implements TtsClient {

    private static final byte[] ID3_MAGIC = new byte[] {0x49, 0x44, 0x33};
    private static final int PAYLOAD_LENGTH = 100;

    @Override
    public byte[] synthesize(String text, Locale lang) {
        ByteBuffer buffer = ByteBuffer.allocate(PAYLOAD_LENGTH);
        buffer.put(ID3_MAGIC);
        buffer.put((byte) 0x03);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x0A);

        String tag = "ECHO-TTS-STUB:"
                + (lang == null ? Locale.US.toLanguageTag() : lang.toLanguageTag());
        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
        int tagWritten = Math.min(tagBytes.length, buffer.remaining());
        buffer.put(tagBytes, 0, tagWritten);

        while (buffer.hasRemaining()) {
            buffer.put((byte) 0x00);
        }
        return buffer.array();
    }
}
