package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.SettingResponse;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 편집 가능한 게임화 수치 / 메시지 설정을 조회·재정의·초기화한다.
// 키 목록과 형식은 여기서 단일 정의하고, 현재 적용값은 RuntimeSettings 에서 읽는다.
@Service
public class SettingsAdminService {

    private enum Type { INT, DOUBLE, STRING }

    // 편집 가능한 설정 한 건의 정의: 키 / 형식 / 현재 적용값 공급자.
    private record Definition(String key, Type type, Supplier<String> currentValue) {}

    private final SettingsService settings;
    private final List<Definition> definitions;

    public SettingsAdminService(SettingsService settings, RuntimeSettings runtime) {
        this.settings = settings;
        this.definitions = List.of(
                new Definition(RuntimeSettings.PASS_THRESHOLD, Type.DOUBLE,
                        () -> String.valueOf(runtime.passThreshold())),
                new Definition(RuntimeSettings.COMPLETION_EXP, Type.INT,
                        () -> String.valueOf(runtime.completionExp())),
                new Definition(RuntimeSettings.STREAK_CAP, Type.INT,
                        () -> String.valueOf(runtime.streakCap())),
                new Definition(RuntimeSettings.DAILY_RECOMMENDED, Type.INT,
                        () -> String.valueOf(runtime.dailyRecommended())),
                new Definition(RuntimeSettings.PRIOR_ATTEMPTS_CAP, Type.INT,
                        () -> String.valueOf(runtime.priorAttemptsCap())),
                new Definition(RuntimeSettings.SCORE_FALLBACK_ON_ERROR, Type.DOUBLE,
                        () -> String.valueOf(runtime.scoreFallbackOnError())),
                new Definition(RuntimeSettings.WEEKLY_TOP_N, Type.INT,
                        () -> String.valueOf(runtime.weeklyTopN())),
                new Definition(RuntimeSettings.WEEKLY_WINDOW_DAYS, Type.INT,
                        () -> String.valueOf(runtime.weeklyWindowDays())),
                new Definition(RuntimeSettings.DEFAULT_RANKING_UNIT_TITLE, Type.STRING,
                        runtime::defaultRankingUnitTitle),
                new Definition(RuntimeSettings.DEFAULT_PRACTICE_WORD, Type.STRING,
                        runtime::defaultPracticeWord),
                new Definition(RuntimeSettings.MSG_RECORDING_GUIDANCE, Type.STRING,
                        runtime::recordingGuidanceFallback),
                new Definition(RuntimeSettings.MSG_FEEDBACK_GUIDANCE, Type.STRING,
                        runtime::feedbackGuidanceFallback),
                new Definition(RuntimeSettings.MSG_RETRY_GUIDANCE, Type.STRING,
                        runtime::retryGuidanceFallback),
                new Definition(RuntimeSettings.MSG_UPLOAD_TOO_LARGE, Type.STRING,
                        runtime::uploadTooLarge),
                new Definition(RuntimeSettings.MSG_TTS_TEXT_REQUIRED, Type.STRING,
                        runtime::ttsTextRequired));
    }

    public List<SettingResponse> list() {
        return definitions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SettingResponse update(String key, String value) {
        Definition definition = find(key);
        validate(definition.type(), value);
        settings.set(key, value);
        return toResponse(definition);
    }

    @Transactional
    public SettingResponse reset(String key) {
        Definition definition = find(key);
        settings.clear(key);
        return toResponse(definition);
    }

    private SettingResponse toResponse(Definition definition) {
        return new SettingResponse(
                definition.key(),
                definition.currentValue().get(),
                definition.type().name(),
                settings.getOverride(definition.key()).isPresent());
    }

    private Definition find(String key) {
        return definitions.stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 설정 키: " + key));
    }

    private void validate(Type type, String value) {
        try {
            if (type == Type.INT) {
                Integer.parseInt(value.trim());
            } else if (type == Type.DOUBLE) {
                Double.parseDouble(value.trim());
            }
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "형식에 맞지 않는 값입니다: " + value);
        }
    }
}
