package com.capstoneecho.echo_back.pronunciation.recording.dto;

import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.time.Instant;
import java.util.List;

// 한 사용자가 이전에 끝낸 step / sentence 의 녹음 한 묶음.
// FE 가 "이어서" 진입 시 이 응답으로 이전 채팅 카드 (봇 안내 + 사용자 점수 + 압축 피드백) 를 다시 그린다.
//   passThreshold : 응답 시점의 통과 임계 (어드민이 바꿀 수 있는 동적 값) — FE 가 historical 점수의 통과 여부 판정에 사용.
//   items         : step / sentence 별 가장 최근 녹음 한 건씩. 시간 순 (오래된 것부터).
public record RecordingHistoryResponse(
        double passThreshold,
        List<Item> items
) {

    // 한 step / sentence 의 압축 피드백 데이터.
    //   recordingId          : 종합 피드백 generate 호출 시 사용 (학습 흐름 재개 시 마지막 step 까지의 latest id 묶음에 포함).
    //   stepId               : chapter 모드는 LearningStep.id, session 모드는 SessionSentence.id.
    //   stepScore            : 0~100. 모델 분석 실패 등으로 null 일 수 있다.
    //   guidanceKr           : LLM 이 만든 한국어 가이드. 없으면 빈 문자열.
    //   perceived / canonical: 모델 음소 시퀀스 (공백 split 결과). 빈 리스트면 음소 시각화는 생략된다.
    //   errors               : 모델/백엔드가 계산한 음소 오류 시퀀스 — 음소 정렬 시각화 데이터.
    //   wrongWords           : LLM 이 짚은 약점 단어 + 0-based 인덱스 — 단어 강조용.
    //   createdAt            : 녹음 시각 — UI 가 "언제 한 시도" 안내가 필요하면 사용.
    public record Item(
            Long recordingId,
            Long stepId,
            Double stepScore,
            String guidanceKr,
            List<String> perceived,
            List<String> canonical,
            List<AnalyzeError> errors,
            List<WrongWord> wrongWords,
            Instant createdAt
    ) {}
}
