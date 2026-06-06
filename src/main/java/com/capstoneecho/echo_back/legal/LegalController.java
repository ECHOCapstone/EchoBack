package com.capstoneecho.echo_back.legal;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 약관 본문 + 버전을 공개로 노출한다. 회원가입 폼이 모달로 본문을 열어 보여줄 때 호출한다.
// SecurityConfig 가 /api/legal/** 를 인증 없이 통과시켜야 한다 (회원가입 전에 호출되므로).
@RestController
@RequestMapping("/api/legal")
public class LegalController {

    private final LegalTermsRegistry registry;

    public LegalController(LegalTermsRegistry registry) {
        this.registry = registry;
    }

    // 현재 버전의 모든 약관 본문을 한 번에 돌려준다 — 회원가입 화면이 한 번의 요청으로 모달을 채울 수 있다.
    @GetMapping("/terms")
    public ApiResponse<TermsResponse> terms() {
        Map<String, String> bodies = new LinkedHashMap<>();
        for (TermsKind kind : TermsKind.values()) {
            bodies.put(kind.name().toLowerCase(), registry.body(kind));
        }
        return ApiResponse.success(new TermsResponse(registry.version(), bodies));
    }

    public record TermsResponse(String version, Map<String, String> bodies) {}
}
