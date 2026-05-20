package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 모델 서버가 돌려주는 한 음소 오류. snake_case (canonical_index) 와 camelCase 양쪽을 모두 받는다.
// perceived 는 모델 서버가 `recognized` 키로 보내는 경우도 있어 그 별칭도 같이 매핑.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeError(
        String op,
        @JsonAlias("canonical_index") Integer canonicalIndex,
        @JsonAlias("recognized") String perceived,
        String canonical
) {}
