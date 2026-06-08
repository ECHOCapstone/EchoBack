package com.capstoneecho.echo_back.pronunciation.phoneme.service;

import com.capstoneecho.echo_back.external.llm.canonical.PhonemeInventory;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.pronunciation.phoneme.dto.PhonemeArticulationResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// 음소 조음 안내의 조회·이미지 서빙. 음차/설명은 PhonemeInventory(SSOT), 이미지는 번들된
// classpath 리소스(content/articulation/{CODE}.png)에서 가져온다. 고정 참조셋이라 DB·외부 스토리지가 없다.
@Service
public class PhonemeArticulationService {

    private static final String IMAGE_DIR = "content/articulation/";
    // 번들 이미지는 모두 PNG 다.
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    private final List<PhonemeArticulationResponse> articulations;
    // 이미지를 서빙할 수 있는 음소 코드(대문자). 경로 파라미터 검증의 화이트리스트이자 경로 조작 방어선.
    private final Set<String> codes;

    public PhonemeArticulationService(PhonemeInventory inventory) {
        this.articulations = inventory.articulationPhonemes().stream()
                .map(p -> new PhonemeArticulationResponse(
                        p.code(), p.koreanCue(), p.tip(),
                        PhonemeArticulationResponse.imageUrl(p.code())))
                .toList();
        this.codes = this.articulations.stream()
                .map(PhonemeArticulationResponse::phoneme)
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<PhonemeArticulationResponse> list() {
        return articulations;
    }

    public ImageData image(String rawPhoneme) {
        String code = normalize(rawPhoneme);
        ClassPathResource resource = new ClassPathResource(IMAGE_DIR + code + ".png");
        try (InputStream in = resource.getInputStream()) {
            return new ImageData(in.readAllBytes(), IMAGE_CONTENT_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read phoneme image: " + code, e);
        }
    }

    // 인벤토리에 없는 토큰(오타·SILENCE·임의 입력)은 404 로 막는다. 화이트리스트라 경로 조작도 차단된다.
    private String normalize(String rawPhoneme) {
        String code = rawPhoneme == null ? "" : rawPhoneme.trim().toUpperCase(Locale.ROOT);
        if (!codes.contains(code)) {
            throw new BusinessException(ErrorCode.PHONEME_ASSET_NOT_FOUND);
        }
        return code;
    }

    public record ImageData(byte[] bytes, String contentType) {}
}
