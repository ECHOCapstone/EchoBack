-- LLM canonical 캐시 컬럼 / 인덱스 제거. canonical 은 step / retry / challenge 흐름에서 사용자 호출 시점에
-- LLM 응답으로 동일 호출에서 함께 생성하므로 영속 캐시가 불필요해진다. V18 의 흔적을 일괄 제거한다.

DROP INDEX ix_learning_steps_canonical_version ON learning_steps;
DROP INDEX ix_session_sentences_canonical_version ON session_sentences;
DROP INDEX ix_daily_challenges_canonical_version ON daily_challenges;

ALTER TABLE learning_steps
    DROP COLUMN canonical_cached_json,
    DROP COLUMN canonical_version;

ALTER TABLE session_sentences
    DROP COLUMN canonical_cached_json,
    DROP COLUMN canonical_version;

ALTER TABLE daily_challenges
    DROP COLUMN canonical_cached_json,
    DROP COLUMN canonical_version;
