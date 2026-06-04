package com.capstoneecho.echo_back.admin.dto;

import java.util.List;

// 배지 관리 화면이 한 번에 받아가는 응답. 어떤 condition 들이 평가 가능한지(어드민의 선택지)도 함께 노출.
public record BadgeCatalogResponse(List<BadgeResponse> badges, List<String> availableConditions) {
}
