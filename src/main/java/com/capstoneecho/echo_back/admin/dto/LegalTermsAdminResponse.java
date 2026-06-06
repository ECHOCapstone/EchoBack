package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import java.util.List;

// 어드민 약관 관리 화면이 한 번에 받는 응답.
//   version    : 현재 버전 (app.legal.terms-version)
//   entries    : 종류별 본문 + override 여부
//   seedStatus : 외부 영구 저장본 디렉터리 상태 (다른 도메인과 같은 형식)
public record LegalTermsAdminResponse(
        String version,
        List<Entry> entries,
        SeedFileStatus seedStatus
) {

    public record Entry(String kind, String label, String body, boolean overridden) {}
}
