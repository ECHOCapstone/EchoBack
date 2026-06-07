-- 학습 진행 상태 보존. 사용자가 챕터 / 세션 학습 도중 이탈 후 다시 진입했을 때
-- 마지막으로 완료한 step / sentence 인덱스를 보존해 "이어서" 흐름을 제공한다.
-- (user_id, script_id) / (user_id, session_id) 가 각각 unique — 한 사용자당 한 단위에 진행 상태는 하나뿐이다.

CREATE TABLE chapter_progress (
    id                          BIGINT NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT NOT NULL,
    script_id                   BIGINT NOT NULL,
    last_completed_step_index   INT    NOT NULL DEFAULT -1,
    started_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_chapter_progress_user_script UNIQUE (user_id, script_id),
    CONSTRAINT fk_chapter_progress_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_progress_script
        FOREIGN KEY (script_id) REFERENCES scripts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE session_progress (
    id                              BIGINT NOT NULL AUTO_INCREMENT,
    user_id                         BIGINT NOT NULL,
    session_id                      BIGINT NOT NULL,
    last_completed_sentence_index   INT    NOT NULL DEFAULT -1,
    started_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_session_progress_user_session UNIQUE (user_id, session_id),
    CONSTRAINT fk_session_progress_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_progress_session
        FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
