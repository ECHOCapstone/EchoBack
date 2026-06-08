-- generate 멱등성: 같은 (user, 녹음 집합) 으로 피드백을 반복 생성해 경험치를 복제하는 우회를 막는다.
-- complete 는 row 당 멱등(WHERE completed=false)이지만 generate 가 매번 새 row 를 INSERT 해서, 같은 입력으로
-- N 번 호출하면 N 개 피드백을 각각 complete 해 경험치가 N 배가 되는 구멍이 있었다.
--
-- recording_ids_hash = SHA-256(scriptId/sessionId + 정렬된 recordingIds). 새 피드백은 generate 가 항상 채우고,
-- (user_id, recording_ids_hash) 유니크 제약이 중복 row 생성을 DB 레벨에서 거부한다.
-- 이 컬럼 도입 이전에 만들어진 row 는 해시를 역산할 수 없어 NULL 로 둔다 — MySQL 유니크 인덱스에서 NULL 은
-- 서로 distinct 로 취급돼 기존 row 끼리 충돌하지 않는다 (새 row 만 비-NULL 해시로 유일성이 강제된다).
ALTER TABLE pronunciation_feedbacks
    ADD COLUMN recording_ids_hash VARCHAR(64) NULL;

ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT uq_feedback_user_recording_hash
        UNIQUE (user_id, recording_ids_hash);
