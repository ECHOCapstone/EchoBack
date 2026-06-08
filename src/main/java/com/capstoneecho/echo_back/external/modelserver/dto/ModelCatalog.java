package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 GET /models 응답: 선택 가능한 음소인식 모델 후보 + 현재 활성 모델 id.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCatalog(String active, List<ModelInfo> models) {

    public List<ModelInfo> safeModels() {
        return models == null ? List.of() : models;
    }

    // requiresCanonical: 추론에 canonical 음소열이 필요한지. FiLM 모델만 true, baseline/slplab 은 false.
    // 활성 모델만 모델 서버가 확정값을 주고, 비활성 모델은 로드 전이라 null(미상)로 온다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
            String id,
            String label,
            String type,
            @JsonAlias("requires_canonical") Boolean requiresCanonical) {}
}
