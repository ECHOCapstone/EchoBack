-- 회원 탈퇴를 soft delete 로 표시한다.
--   deleted_at : 탈퇴 시각. NULL 이면 활성 회원.
-- 학습 기록과 녹음은 즉시 지우지 않는다 — 개인정보처리방침이 약속한 30일 grace period 동안 보존되며,
-- 별도 정리 작업이 그 후 hard delete 한다. 인증 단계에서 deleted_at 이 채워진 사용자는 거절된다.
ALTER TABLE users
    ADD COLUMN deleted_at DATETIME(6) NULL;

-- 활성 회원만 빠르게 조회하기 위한 부분 인덱스 대용. MySQL 은 partial index 가 없으므로
-- 그냥 일반 인덱스로 둔다 (조회 빈도가 낮아 비용보다 가치가 큼).
CREATE INDEX ix_users_deleted_at ON users (deleted_at);
