package com.capstoneecho.echo_back.statistics.badges;

// 시스템 배지 한 정의. AppProperties.Stats.Badge 와 같은 형태지만 어드민이 런타임에 추가/수정/삭제 가능한
// 도메인 객체로 분리해 둔다.
//   id        : 고유 식별자 (StatsResponse.Badge.id 와 짝). 어드민이 새로 만들 때는 영문 대문자/숫자/언더스코어
//                패턴을 권장하지만 검증은 컨트롤러 측에서만 한다.
//   name      : 사용자 화면에 노출되는 한국어 이름.
//   condition : BadgeCondition enum 이름. 평가 가능한 식별자만 허용.
//   threshold : 조건의 임계값 (회수/일수/점수). condition 마다 의미가 다르다.
public record BadgeDef(String id, String name, String condition, int threshold) {

    public BadgeDef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("condition is required");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must be >= 0");
        }
    }
}
