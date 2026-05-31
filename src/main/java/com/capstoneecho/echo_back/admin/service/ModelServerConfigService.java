package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.ModelServerConfigResponse;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.ModelServerSettingKeys;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 어드민의 음소인식 모델 선택을 읽고 적용한다.
// 후보 목록의 단일 출처는 모델 서버 /models 이고, 선택값은 SettingsService 오버라이드로 보관한다.
// (ModelServerClient 가 /analyze 호출 시 이 선택값을 model 파라미터로 함께 보낸다.)
@Service
public class ModelServerConfigService {

    private final ModelServerClient modelServerClient;
    private final SettingsService settings;

    public ModelServerConfigService(ModelServerClient modelServerClient, SettingsService settings) {
        this.modelServerClient = modelServerClient;
        this.settings = settings;
    }

    public ModelServerConfigResponse current() {
        String selected = settings.getOrDefault(ModelServerSettingKeys.MODEL, "");
        try {
            ModelCatalog catalog = modelServerClient.models();
            // 아직 고른 적 없으면 모델 서버의 활성 모델을 현재값으로 보여준다.
            if (selected.isBlank() && catalog.active() != null) {
                selected = catalog.active();
            }
            return new ModelServerConfigResponse(selected, catalog.safeModels(), true);
        } catch (BusinessException e) {
            // 모델 서버가 떠 있지 않아도 저장된 선택값은 보여준다 (후보는 비움).
            return new ModelServerConfigResponse(selected, List.of(), false);
        }
    }

    @Transactional
    public ModelServerConfigResponse update(String model) {
        ModelCatalog catalog = modelServerClient.models();
        boolean known = catalog.safeModels().stream().anyMatch(m -> m.id().equals(model));
        if (!known) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 모델: " + model);
        }
        settings.set(ModelServerSettingKeys.MODEL, model);
        return current();
    }
}
