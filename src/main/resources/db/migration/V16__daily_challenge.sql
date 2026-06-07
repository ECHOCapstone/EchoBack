-- "오늘의 챌린지" 도메인을 도입한다.
--
-- daily_challenges          : 어드민이 등록한 챌린지. 한 번에 하나만 active=1 이 되도록 도메인 서비스가
--                             보장하며, 새 챌린지 활성화 시 이전 챌린지는 자동으로 active=0 + deactivated_at 기록.
-- daily_challenge_attempts  : 사용자가 활성 챌린지에 발음 도전한 결과. best 점수가 랭킹의 SSOT.
--                             하루 N회 제한 카운트와 시도 추이 통계 모두 created_at 기반.
-- daily_challenge_rewards   : 챌린지 종료 시점에 1~3등에게 발급된 배지 기록. 한 챌린지/사용자 조합은 유니크.

CREATE TABLE daily_challenges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_text VARCHAR(500) NOT NULL,
    korean_translation VARCHAR(500),
    active BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    activated_at DATETIME(6),
    deactivated_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- 활성 챌린지 조회 (사용자 / 어드민) 가 매 요청마다 일어나므로 인덱스로 가속.
CREATE INDEX ix_daily_challenges_active ON daily_challenges (active);
-- 히스토리 페이지가 최신순 페이징 — 인덱스가 있어야 누적 후 풀스캔이 안 된다.
CREATE INDEX ix_daily_challenges_created ON daily_challenges (created_at);

CREATE TABLE daily_challenge_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    challenge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    audio_path VARCHAR(500) NOT NULL,
    perceived TEXT,
    canonical TEXT,
    errors_json TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dca_challenge FOREIGN KEY (challenge_id) REFERENCES daily_challenges(id),
    CONSTRAINT fk_dca_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- 챌린지별 사용자 best 점수 조회 (랭킹) — challenge_id + user_id + score 의 인덱스 한 번에 best 추출.
CREATE INDEX ix_dca_challenge_user_score ON daily_challenge_attempts (challenge_id, user_id, score);
-- 하루 N회 제한 카운트 (user_id, created_at 범위 SCAN).
CREATE INDEX ix_dca_user_created ON daily_challenge_attempts (user_id, created_at);

CREATE TABLE daily_challenge_rewards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    challenge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rank_at_close INT NOT NULL,
    best_score DOUBLE NOT NULL,
    badge_id VARCHAR(50) NOT NULL,
    awarded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dcr_challenge_user UNIQUE (challenge_id, user_id),
    CONSTRAINT fk_dcr_challenge FOREIGN KEY (challenge_id) REFERENCES daily_challenges(id),
    CONSTRAINT fk_dcr_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- 사용자 프로필 화면이 "내가 받은 챌린지 배지" 를 시간순으로 노출 — 인덱스로 빠르게.
CREATE INDEX ix_dcr_user_awarded ON daily_challenge_rewards (user_id, awarded_at);
