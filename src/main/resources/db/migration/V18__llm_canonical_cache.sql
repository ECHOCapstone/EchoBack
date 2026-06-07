-- LLM canonical 캐시 컬럼 도입. 발화 단위 (=한 문장 / 한 단어) 인 learning_steps / session_sentences /
-- daily_challenges 에 단어별 ARPABET 시퀀스 JSON 을 캐싱한다.
--
-- canonical_cached_json : LlmCanonicalGenerator 가 만든 {"words":[{"word":"...","phonemes":[...]}]}.
--                         NULL = 아직 생성된 적 없음. attempt 시점에 lazy 로 채워진다.
-- canonical_version     : 1 = legacy g2p (없음), 2 = LLM 생성본. 기본 1.
--                         기존 row 는 lazy backfill 흐름에서 2 로 업그레이드된다.
--
-- scripts 에는 챕터 본문 (`content`) 이 통째로 들어 있지만 채점은 step.target_text 단위로 일어나므로
-- 캐시는 step / sentence / challenge 단위에만 둔다.

ALTER TABLE learning_steps
    ADD COLUMN canonical_cached_json LONGTEXT NULL,
    ADD COLUMN canonical_version INT NOT NULL DEFAULT 1;

ALTER TABLE session_sentences
    ADD COLUMN canonical_cached_json LONGTEXT NULL,
    ADD COLUMN canonical_version INT NOT NULL DEFAULT 1;

ALTER TABLE daily_challenges
    ADD COLUMN canonical_cached_json LONGTEXT NULL,
    ADD COLUMN canonical_version INT NOT NULL DEFAULT 1;

-- 어드민 일괄 backfill 이 canonical_version 으로 미적용 row 만 페이지 단위 SELECT — 풀스캔 회피.
CREATE INDEX ix_learning_steps_canonical_version ON learning_steps (canonical_version);
CREATE INDEX ix_session_sentences_canonical_version ON session_sentences (canonical_version);
CREATE INDEX ix_daily_challenges_canonical_version ON daily_challenges (canonical_version);
