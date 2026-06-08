package com.capstoneecho.echo_back.external.llm.canonical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// classpath:content/phoneme-inventory.json 을 로드해 ARPABET 음소의 단일 출처를 노출한다.
// 두 곳에서 사용한다:
//   - canonical 프롬프트에 끼울 인벤토리 마크다운 테이블 (markdownTable()) — LLM 이 모르는 음소를 만들지 않게.
//   - 모델 서버 응답 토큰의 표준 여부 판정 — PhonemeMismatchInspector 가 codes() 를 SSOT 로 참조한다.
@Component
public class PhonemeInventory {

    private static final String RESOURCE = "content/phoneme-inventory.json";

    private final List<Phoneme> phonemes;
    private final Set<String> codes;
    private final String markdownTable;

    public PhonemeInventory(ObjectMapper objectMapper) {
        InventoryFile file = readFile(objectMapper);
        this.phonemes = List.copyOf(file.phonemes() == null ? List.of() : file.phonemes());
        this.codes = this.phonemes.stream()
                .map(p -> p.code().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.markdownTable = renderMarkdownTable(this.phonemes);
    }

    // 알려진 표준 음소 코드 집합 (대문자). SIL / SP 등 비분석 토큰도 포함.
    public Set<String> codes() {
        return codes;
    }

    // 주어진 토큰이 인벤토리 안에 있는지. 대소문자 / 공백을 흡수한다.
    public boolean contains(String token) {
        if (token == null) {
            return false;
        }
        String key = token.trim().toUpperCase(Locale.ROOT);
        return !key.isEmpty() && codes.contains(key);
    }

    public List<Phoneme> phonemes() {
        return phonemes;
    }

    // 조음 안내(음차·설명·이미지)를 제공할 음소들. 실제 발음이 없는 SILENCE 토큰은 제외한다.
    public List<Phoneme> articulationPhonemes() {
        return phonemes.stream()
                .filter(p -> !"SILENCE".equalsIgnoreCase(p.category()))
                .toList();
    }

    // LLM 프롬프트의 {{inventory}} 자리에 끼울 마크다운 테이블.
    // SIL / SP 같은 SILENCE 토큰은 제외 — canonical 출력에 절대 등장하면 안 되므로 모델 컨텍스트에도 노출하지 않는다.
    public String markdownTable() {
        return markdownTable;
    }

    private static InventoryFile readFile(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, InventoryFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("phoneme inventory resource not found: " + RESOURCE, e);
        }
    }

    private static String renderMarkdownTable(List<Phoneme> phonemes) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ARPABET | category | 한글 음차 |\n");
        sb.append("|---------|----------|----------|\n");
        for (Phoneme p : phonemes) {
            String category = p.category() == null ? "" : p.category();
            if ("SILENCE".equalsIgnoreCase(category)) {
                continue;
            }
            String cue = p.koreanCue() == null ? "" : p.koreanCue();
            sb.append("| ").append(p.code())
                    .append(" | ").append(category)
                    .append(" | ").append(cue)
                    .append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InventoryFile(List<Phoneme> phonemes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Phoneme(String code, String category, String koreanCue, String tip) {}
}
