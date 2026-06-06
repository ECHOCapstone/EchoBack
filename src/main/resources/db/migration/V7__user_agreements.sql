-- users 테이블에 약관 동의 시점들을 저장한다.
--   terms_version          : 동의한 약관 버전 (app.legal.terms-version 값). 추후 약관 변경 시
--                            재동의 요구 판정의 단일 출처.
--   agreed_terms_at        : 이용약관 동의 시각 (필수 동의)
--   agreed_privacy_at      : 개인정보처리방침 동의 시각 (필수 동의)
--   agreed_age_over14_at   : 만 14세 이상 동의 시각 (한국 PIPA 표준)
--   agreed_marketing_at    : 마케팅 정보 수신 동의 시각 (선택. NULL 이면 미동의)
-- 신규 가입 흐름에서 4개 시각이 함께 기록되며, 마케팅만 NULL 허용한다.
ALTER TABLE users
    ADD COLUMN terms_version        VARCHAR(20) NULL,
    ADD COLUMN agreed_terms_at      DATETIME(6) NULL,
    ADD COLUMN agreed_privacy_at    DATETIME(6) NULL,
    ADD COLUMN agreed_age_over14_at DATETIME(6) NULL,
    ADD COLUMN agreed_marketing_at  DATETIME(6) NULL;
