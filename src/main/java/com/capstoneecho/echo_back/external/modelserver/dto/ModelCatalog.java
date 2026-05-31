package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 GET /models 응답: 선택 가능한 음소인식 모델 후보 + 현재 활성 모델 id.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelCatalog(String active, List<ModelInfo> models) {

    public List<ModelInfo> safeModels() {
        return models == null ? List.of() : models;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(String id, String label, String type) {}
}
