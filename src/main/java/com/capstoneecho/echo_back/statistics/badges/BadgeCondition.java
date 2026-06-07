package com.capstoneecho.echo_back.statistics.badges;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// 시스템 배지가 평가에 쓸 수 있는 condition 종류. 평가 로직은 BadgePolicy 가 enum 별로 가지고 있으므로
// 새 종류를 추가하면 양쪽을 같이 손봐야 한다.
//   COMPLETED_FEEDBACK_COUNT — 완료된 종합 피드백 수 (챕터 완료 카운트와 사실상 동치).
//   USER_STREAK              — 현재 연속 학습 일수.
//   TOTAL_EXP                — 누적 EXP. threshold 점 이상이면 달성.
//   RECORDING_COUNT          — 누적 녹음 시도 횟수.
//   COMPLETED_TRACK_COUNT    — 모든 챕터의 종합 피드백을 완료한 트랙 수. threshold 개 이상이면 달성.
public enum BadgeCondition {
    COMPLETED_FEEDBACK_COUNT,
    USER_STREAK,
    TOTAL_EXP,
    RECORDING_COUNT,
    COMPLETED_TRACK_COUNT;

    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) return false;
        for (BadgeCondition c : values()) {
            if (c.name().equals(raw)) return true;
        }
        return false;
    }

    // 어드민 UI 가 선택지를 보여줄 때 사용한다.
    public static List<String> allNames() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
    }
}
