-- 운영 누적 데이터에 비례해 무거워지는 학습 흐름 조회와 retention 작업의 인덱스.
--
-- recordings 는 한 사용자가 한 스크립트에서 step / sentence 마다 여러 시도를 누적한다.
-- 단일 컬럼 FK 인덱스만 있으면 user 필터링 후 step / created_at 정렬이 매번 풀스캔되어
-- 누적 50건만 넘어도 학습 응답 시간에 가시적인 지연이 끼게 된다.
--
-- translation_cache 는 만료 기준이 created_at 인데 PK(SHA-256) 외 인덱스가 없어 retention
-- 잡이 풀스캔으로만 가능했다.

CREATE INDEX ix_recordings_user_script_step_created
    ON recordings (user_id, script_id, step_id, created_at);

CREATE INDEX ix_recordings_user_session_sentence_created
    ON recordings (user_id, session_id, session_sentence_id, created_at);

CREATE INDEX ix_recordings_user_created
    ON recordings (user_id, created_at);

CREATE INDEX ix_translation_cache_created
    ON translation_cache (created_at);
