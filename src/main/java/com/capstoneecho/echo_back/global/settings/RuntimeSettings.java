package com.capstoneecho.echo_back.global.settings;

import com.capstoneecho.echo_back.global.config.AppProperties;
import org.springframework.stereotype.Service;

// 게임화 수치 / 메시지의 런타임 접근점. yaml 기본값 위에 SettingsService 오버라이드를 얹어
// 호출 시점에 읽는다. 어드민이 값을 바꾸면 다음 호출부터 즉시 반영된다.
// 오버라이드가 없으면 yaml 기본값을 그대로 돌려주므로 기존 동작과 동일하다.
@Service
public class RuntimeSettings {

    // 편집 가능한 설정 키. 어드민 API 와 공유한다.
    public static final String PASS_THRESHOLD = "gamification.passThreshold";
    public static final String COMPLETION_EXP = "gamification.completionExp";
    public static final String STREAK_CAP = "gamification.streakCap";
    public static final String DAILY_RECOMMENDED = "gamification.dailyRecommended";
    public static final String PRIOR_ATTEMPTS_CAP = "gamification.priorAttemptsCap";
    public static final String SCORE_FALLBACK_ON_ERROR = "gamification.scoreFallbackOnError";
    public static final String WEEKLY_TOP_N = "gamification.weeklyTopN";
    public static final String WEEKLY_WINDOW_DAYS = "gamification.weeklyWindowDays";
    public static final String DEFAULT_RANKING_UNIT_TITLE = "gamification.defaultRankingUnitTitle";
    public static final String DEFAULT_PRACTICE_WORD = "gamification.defaultPracticeWord";
    public static final String MSG_RECORDING_GUIDANCE = "messages.recordingGuidanceFallback";
    public static final String MSG_FEEDBACK_GUIDANCE = "messages.feedbackGuidanceFallback";
    public static final String MSG_RETRY_GUIDANCE = "messages.retryGuidanceFallback";
    public static final String MSG_UPLOAD_TOO_LARGE = "messages.uploadTooLarge";
    public static final String MSG_TTS_TEXT_REQUIRED = "messages.ttsTextRequired";

    private final SettingsService settings;
    private final AppProperties.Gamification gamification;
    private final AppProperties.Messages messages;

    public RuntimeSettings(SettingsService settings, AppProperties appProperties) {
        this.settings = settings;
        this.gamification = appProperties.gamification();
        this.messages = appProperties.messages();
    }

    public double passThreshold() {
        return settings.getDouble(PASS_THRESHOLD, gamification.passThreshold());
    }

    public int completionExp() {
        return settings.getInt(COMPLETION_EXP, gamification.completionExp());
    }

    public int streakCap() {
        return settings.getInt(STREAK_CAP, gamification.streakCap());
    }

    public int dailyRecommended() {
        return settings.getInt(DAILY_RECOMMENDED, gamification.dailyRecommended());
    }

    public int priorAttemptsCap() {
        // 최소 1 보장: 어드민이 0/음수를 넣어도 직전 시도 한 건은 LLM 입력에 포함되도록 클램프한다.
        return Math.max(1, settings.getInt(PRIOR_ATTEMPTS_CAP, gamification.priorAttemptsCap()));
    }

    public double scoreFallbackOnError() {
        return settings.getDouble(SCORE_FALLBACK_ON_ERROR, gamification.scoreFallbackOnError());
    }

    public int weeklyTopN() {
        return settings.getInt(WEEKLY_TOP_N, gamification.weeklyTopN());
    }

    public int weeklyWindowDays() {
        return settings.getInt(WEEKLY_WINDOW_DAYS, gamification.weeklyWindowDays());
    }

    public String defaultRankingUnitTitle() {
        return settings.getOrDefault(DEFAULT_RANKING_UNIT_TITLE, gamification.defaultRankingUnitTitle());
    }

    public String defaultPracticeWord() {
        return settings.getOrDefault(DEFAULT_PRACTICE_WORD, gamification.defaultPracticeWord());
    }

    public String recordingGuidanceFallback() {
        return message(MSG_RECORDING_GUIDANCE, messages == null ? null : messages.recordingGuidanceFallback());
    }

    public String feedbackGuidanceFallback() {
        return message(MSG_FEEDBACK_GUIDANCE, messages == null ? null : messages.feedbackGuidanceFallback());
    }

    public String retryGuidanceFallback() {
        return message(MSG_RETRY_GUIDANCE, messages == null ? null : messages.retryGuidanceFallback());
    }

    public String uploadTooLarge() {
        return message(MSG_UPLOAD_TOO_LARGE, messages == null ? null : messages.uploadTooLarge());
    }

    public String ttsTextRequired() {
        return message(MSG_TTS_TEXT_REQUIRED, messages == null ? null : messages.ttsTextRequired());
    }

    private String message(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue == null ? "" : defaultValue);
    }
}
