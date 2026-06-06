package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.admin.dto.LegalTermsAdminResponse;
import com.capstoneecho.echo_back.admin.dto.LegalTermsUpdateRequest;
import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.legal.LegalTermsRegistry;
import com.capstoneecho.echo_back.legal.TermsKind;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 어드민이 약관 본문을 직접 편집한다. /api/admin/** 는 SecurityConfig 가 ROLE_ADMIN 으로 보호한다.
//   GET    /api/admin/legal/terms              — 종류별 본문 + override 여부 + seedStatus
//   PUT    /api/admin/legal/terms/{kind}       — 한 종류 본문 갱신 (영구 저장본까지 즉시 기록)
//   POST   /api/admin/legal/terms/{kind}/reset — 한 종류만 공장 기본값으로 되돌림
//   POST   /api/admin/legal/reset              — 모든 종류 공장 기본값으로
@RestController
@RequestMapping("/api/admin/legal")
public class LegalAdminController {

    // 종류별 한국어 라벨 — 어드민 UI 가 그대로 사용.
    private static final List<KindLabel> KIND_LABELS = List.of(
            new KindLabel(TermsKind.SERVICE, "서비스 이용약관"),
            new KindLabel(TermsKind.PRIVACY, "개인정보처리방침")
    );

    private final LegalTermsRegistry registry;

    public LegalAdminController(LegalTermsRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/terms")
    public ApiResponse<LegalTermsAdminResponse> list() {
        var snapshot = registry.snapshot();
        List<LegalTermsAdminResponse.Entry> entries = new ArrayList<>(snapshot.size());
        for (KindLabel kl : KIND_LABELS) {
            var view = snapshot.get(kl.kind());
            entries.add(new LegalTermsAdminResponse.Entry(
                    kl.kind().name().toLowerCase(),
                    kl.label(),
                    view.body(),
                    view.overridden()));
        }
        return ApiResponse.success(new LegalTermsAdminResponse(
                registry.version(), entries, registry.fileStatus()));
    }

    @PutMapping("/terms/{kind}")
    public ApiResponse<LegalTermsAdminResponse.Entry> update(
            @PathVariable String kind, @Valid @RequestBody LegalTermsUpdateRequest request) {
        TermsKind parsed = parse(kind);
        var view = registry.override(parsed, request.body());
        return ApiResponse.success(new LegalTermsAdminResponse.Entry(
                parsed.name().toLowerCase(), labelOf(parsed), view.body(), view.overridden()));
    }

    @PostMapping("/terms/{kind}/reset")
    public ApiResponse<LegalTermsAdminResponse.Entry> resetSingle(@PathVariable String kind) {
        TermsKind parsed = parse(kind);
        var view = registry.resetSingle(parsed);
        return ApiResponse.success(new LegalTermsAdminResponse.Entry(
                parsed.name().toLowerCase(), labelOf(parsed), view.body(), view.overridden()));
    }

    @PostMapping("/reset")
    public ApiResponse<LegalTermsAdminResponse> resetAll() {
        registry.resetToDefaults();
        return list();
    }

    private static TermsKind parse(String raw) {
        try {
            return TermsKind.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 약관 종류: " + raw);
        }
    }

    private static String labelOf(TermsKind kind) {
        for (KindLabel kl : KIND_LABELS) {
            if (kl.kind() == kind) return kl.label();
        }
        return kind.name();
    }

    private record KindLabel(TermsKind kind, String label) {}
}
