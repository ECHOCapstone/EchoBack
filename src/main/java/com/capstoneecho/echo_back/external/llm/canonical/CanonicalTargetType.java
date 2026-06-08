package com.capstoneecho.echo_back.external.llm.canonical;

// 사용자 단위 canonical lock 의 발화 단위. (user_id, target_type, target_id) 가 lock 의 식별자다.
//   STEP             — 추천 학습 (learning_steps.id)
//   SESSION_SENTENCE — 맞춤 학습의 한 문장 (session_sentences.id)
//   CHALLENGE        — 오늘의 챌린지 (daily_challenges.id)
public enum CanonicalTargetType {
    STEP,
    SESSION_SENTENCE,
    CHALLENGE
}
