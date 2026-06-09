-- 온보딩 튜토리얼 완료 시각. NULL 이면 아직 튜토리얼을 보지 않은 사용자다.
-- 기존 사용자는 가입 시각으로 채워 완료 처리한다 (이미 쓰던 사용자에게 튜토리얼을 새로 강요하지 않는다).
-- 신규 가입자는 엔티티가 NULL 로 삽입하므로, 최초 진입 시 1회 노출된다.
ALTER TABLE users
    ADD COLUMN onboarding_completed_at DATETIME(6) NULL;

UPDATE users
    SET onboarding_completed_at = created_at
    WHERE onboarding_completed_at IS NULL;
