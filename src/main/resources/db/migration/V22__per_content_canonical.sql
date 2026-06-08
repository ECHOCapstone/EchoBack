-- canonical 정책을 per-user lock 에서 per-content 캐시로 단일화한다.
-- (모든 사용자가 같은 정답으로 채점되도록 모델 변경 / 시드 직후 한 번만 생성한다.)
--   - user_canonical_locks 테이블 제거 (V20 도입분 정리).
--   - learning_steps / session_sentences 에 canonical_cached_json 컬럼 추가.
-- daily_challenges.canonical_cached_json 은 V21 에서 이미 추가됨.

DROP TABLE IF EXISTS user_canonical_locks;

ALTER TABLE learning_steps
    ADD COLUMN canonical_cached_json LONGTEXT NULL;

ALTER TABLE session_sentences
    ADD COLUMN canonical_cached_json LONGTEXT NULL;
