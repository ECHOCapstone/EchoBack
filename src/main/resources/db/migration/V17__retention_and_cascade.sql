-- Critical / High 묶음 마이그레이션.
--
-- 1) cascade 정책 교정 (C4)
--    pronunciation_feedbacks.script_id / session_id 와 recordings.script_id / session_id / step_id
--    / session_sentence_id 가 ON DELETE SET NULL 이었으나, 부모가 사라진 자식 행은 학습 history
--    재구성이 불가능해 가치가 없다. CASCADE 로 전환해 부모 삭제 시 자식도 정리한다.
--
-- 2) user_id 계열 cascade 보강 (C2 회원 탈퇴 hard delete 의 자동 정리 기반)
--    retention 스케줄러가 User 를 delete 하면 자식이 자동 정리되도록 user_id FK 도 CASCADE 로 둔다.
--    (이미 chapter_progress / session_progress / daily_challenge_* 는 별도 CASCADE 정의.)
--
-- 3) daily_challenges 활성 단일성 보장 (H2)
--    application 레벨 보장에 더해 DB 레벨로 unique 제약을 둔다. active=1 일 때만 값을 갖는
--    generated 컬럼을 unique index 로 묶어, race 로 두 챌린지가 동시에 active 가 되는 경우를
--    insert/update 시점에 거절한다.
--
-- 4) daily_challenge_attempts (challenge_id, score DESC) 인덱스 (H3)
--    랭킹 추출 (challenge 별 best 점수 정렬) 를 정렬 없이 인덱스 스캔으로 처리하기 위함.

-- ---------- 1) pronunciation_feedbacks ----------
ALTER TABLE pronunciation_feedbacks
    DROP FOREIGN KEY FK6i6dspvoivjw5biuermpj4v76;
ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT fk_pf_script
        FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE;

ALTER TABLE pronunciation_feedbacks
    DROP FOREIGN KEY FKr6x40fj2502xdn0e8majekkkk;
ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT fk_pf_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE;

ALTER TABLE pronunciation_feedbacks
    DROP FOREIGN KEY FK11u9jcmk3iscspr5oggsacwhs;
ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT fk_pf_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- pronunciation_feedbacks 의 XOR CHECK 는 V1 에서 ON DELETE SET NULL FK 와 충돌해 제거됐다.
-- CASCADE 로 바뀐 지금은 자식이 NULL 로 떨어질 일이 없으므로 정합성 측면에서 CHECK 를 다시
-- 강제해도 안전하지만, 도메인 서비스가 이미 검증하므로 DB CHECK 는 추가하지 않는다.

-- ---------- 2) recordings ----------
ALTER TABLE recordings
    DROP FOREIGN KEY FK5juijr01tborfr4ly2mh91be;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_script
        FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE;

ALTER TABLE recordings
    DROP FOREIGN KEY FKfadxbpf0kklubedkjepunr2rh;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE;

ALTER TABLE recordings
    DROP FOREIGN KEY FKtgekoetnnacg5spldg235w8j;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_sentence
        FOREIGN KEY (session_sentence_id) REFERENCES session_sentences(id) ON DELETE CASCADE;

ALTER TABLE recordings
    DROP FOREIGN KEY FKpv1fobrl9cm27weftv1yw5abw;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_step
        FOREIGN KEY (step_id) REFERENCES learning_steps(id) ON DELETE CASCADE;

ALTER TABLE recordings
    DROP FOREIGN KEY FKlr4t7g9f4yrn32vc6it2amwac;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ---------- sessions / social_accounts user_id CASCADE ----------
ALTER TABLE sessions
    DROP FOREIGN KEY FKruie73rneumyyd1bgo6qw8vjt;
ALTER TABLE sessions
    ADD CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE social_accounts
    DROP FOREIGN KEY FK6rmxxiton5yuvu7ph2hcq2xn7;
ALTER TABLE social_accounts
    ADD CONSTRAINT fk_social_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ---------- daily_challenge_* user_id CASCADE ----------
ALTER TABLE daily_challenge_attempts
    DROP FOREIGN KEY fk_dca_user;
ALTER TABLE daily_challenge_attempts
    ADD CONSTRAINT fk_dca_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE daily_challenge_rewards
    DROP FOREIGN KEY fk_dcr_user;
ALTER TABLE daily_challenge_rewards
    ADD CONSTRAINT fk_dcr_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ---------- 3) daily_challenges 활성 단일성 (H2) ----------
-- active=1 일 때만 'X' 를 갖는 generated column 에 unique 제약 — 동시에 두 챌린지가 활성화될 수 없다.
-- 챌린지 비활성화 시 NULL 로 떨어지고 NULL 들은 unique 제약에서 제외되어 비활성 챌린지는 제한 없이 공존한다.
ALTER TABLE daily_challenges
    ADD COLUMN active_marker CHAR(1) AS (CASE WHEN active = 1 THEN 'X' END) VIRTUAL,
    ADD CONSTRAINT uk_daily_challenges_active_marker UNIQUE (active_marker);

-- ---------- 4) daily_challenge_attempts (challenge_id, score DESC) (H3) ----------
-- 챌린지별 랭킹 정렬용. MySQL 8.0 부터 descending index 가 실제 의미를 갖는다.
CREATE INDEX idx_dca_challenge_score
    ON daily_challenge_attempts (challenge_id, score DESC);
