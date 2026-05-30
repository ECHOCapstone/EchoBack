-- 관리자 권한 컬럼. 기존 사용자는 모두 일반 사용자(USER)로 시작한다.
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
