-- 챌린지의 canonical 은 admin 이 등록하는 시점에 LlmCanonicalGenerator 로 한 번 만들어 영속화한다.
-- (사용자 lock 과 달리, 챌린지는 모든 사용자가 같은 정답으로 채점되어야 랭킹이 공정하다.)
-- canonical_json : LLM 응답의 canonicalWords 를 그대로 직렬화한 JSON.
--                  포맷: [{"word":"...","phonemes":[...]}].

ALTER TABLE daily_challenges
    ADD COLUMN canonical_cached_json LONGTEXT NULL;
