package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import java.util.List;

// 현재 적용 중인 음소인식 모델 선택 + 선택 가능한 후보. 어드민 화면이 드롭다운을 그릴 때 쓴다.
// serverAvailable 이 false 면 모델 서버에 연결할 수 없어 후보를 못 받은 상태(저장된 선택값만 노출).
public record ModelServerConfigResponse(
        String selected,
        List<ModelCatalog.ModelInfo> options,
        boolean serverAvailable
) {}
