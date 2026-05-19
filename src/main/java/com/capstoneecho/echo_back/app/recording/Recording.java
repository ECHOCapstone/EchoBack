package com.capstoneecho.echo_back.app.recording;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 한 건의 사용자 녹음 메타.
//
// 참조는 scriptId/sessionId 중 하나만 사용한다. stepId 는 정해진 학습 단계(추천 학습)일 때만.
// audioPath 는 디스크 저장 경로, perceived/canonical/peakSoftmax 는 모델 응답 캐시,
// stepScore 는 ScoringPolicy 로 계산한 단계 점수(0~100).
@Entity
@Table(name = "recordings", indexes = {
        @Index(name = "ix_recordings_user", columnList = "user_id"),
        @Index(name = "ix_recordings_script", columnList = "script_id"),
        @Index(name = "ix_recordings_session", columnList = "session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "script_id")
    private Long scriptId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "step_id")
    private Long stepId;

    // 추천 학습의 step_id 와 동등 위치의 식별자. 사용자 맞춤 세션의 한 문장에 대한 녹음일 때 채워진다.
    @Column(name = "session_sentence_id")
    private Long sessionSentenceId;

    @Column(name = "audio_path", length = 500, nullable = false)
    private String audioPath;

    @Column(name = "duration_sec")
    private Double durationSec;

    @Column(name = "perceived", columnDefinition = "TEXT")
    private String perceived;

    @Column(name = "canonical", columnDefinition = "TEXT")
    private String canonical;

    @Column(name = "peak_softmax", columnDefinition = "TEXT")
    private String peakSoftmax;

    @Column(name = "step_score")
    private Double stepScore;

    // 한 녹음 직후 채팅 흐름에 노출되는 한국어 한 줄 가이드.
    @Column(name = "guidance_kr", columnDefinition = "TEXT")
    private String guidanceKr;

    // ModelAnalyzeResponse.errors 의 JSON 직렬화. FeedbackService 가 unit 종합 시 음소 빈도를 집계한다.
    @Column(name = "errors_json", columnDefinition = "TEXT")
    private String errorsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 추천 학습 트랙의 한 step (LearningStep) 에 대한 녹음.
    public static Recording forScriptStep(Long userId, Long scriptId, Long stepId, String audioPath) {
        return build(userId, scriptId, null, stepId, null, audioPath);
    }

    // 사용자 맞춤 세션의 한 문장 (SessionSentence) 에 대한 녹음.
    public static Recording forSessionSentence(Long userId, Long sessionId, Long sessionSentenceId, String audioPath) {
        return build(userId, null, sessionId, null, sessionSentenceId, audioPath);
    }

    // 사용자 맞춤 세션을 한 호흡으로 통째로 녹음하는 자유 폼 (sentence 분리 없음).
    public static Recording forSessionFreeForm(Long userId, Long sessionId, String audioPath) {
        return build(userId, null, sessionId, null, null, audioPath);
    }

    private static Recording build(
            Long userId,
            Long scriptId,
            Long sessionId,
            Long stepId,
            Long sessionSentenceId,
            String audioPath
    ) {
        var r = new Recording();
        r.userId = userId;
        r.scriptId = scriptId;
        r.sessionId = sessionId;
        r.stepId = stepId;
        r.sessionSentenceId = sessionSentenceId;
        r.audioPath = audioPath;
        return r;
    }

    public void applyAnalysis(AnalysisOutcome outcome) {
        this.perceived = outcome.perceivedJoined();
        this.canonical = outcome.canonicalJoined();
        this.peakSoftmax = outcome.peakSoftmaxJoined();
        this.errorsJson = outcome.errorsJson();
        this.guidanceKr = outcome.guidanceKr();
        this.durationSec = outcome.durationSec();
        this.stepScore = outcome.stepScore();
    }

    // 모델 분석 + 점수 + LLM 가이드까지 한 녹음에 반영할 결과를 한 번에 들고 다닌다.
    public record AnalysisOutcome(
            String perceivedJoined,
            String canonicalJoined,
            String peakSoftmaxJoined,
            String errorsJson,
            String guidanceKr,
            Double durationSec,
            Double stepScore
    ) {}

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
