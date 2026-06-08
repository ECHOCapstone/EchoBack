-- Per-user canonical lock. 같은 사용자가 같은 발화 단위 (step / session sentence / challenge) 를
-- 두 번 이상 시도하면 첫 시도에 LLM 이 만든 canonical 을 그대로 echo 해야 학습자 입장에서 "정답이
-- 시도마다 바뀌는" UX 가 사라진다.
--
-- canonical_json : LLM 응답의 canonicalWords 를 그대로 JSON 으로 직렬화한 본문.
--                  포맷: [{"word":"...","phonemes":[...]}].
-- (user_id, target_type, target_id) 유니크 — race 시 한 쪽만 INSERT 가 통과한다.

CREATE TABLE user_canonical_locks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    canonical_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ucl_user_target UNIQUE (user_id, target_type, target_id),
    CONSTRAINT fk_ucl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;
