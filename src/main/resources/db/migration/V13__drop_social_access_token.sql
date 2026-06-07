-- social_accounts 의 access_token 컬럼을 제거한다.
--
-- 이 컬럼은 provider 가 발급한 access_token 의 평문 보관을 위해 만들어졌으나, 서비스 코드 어디에서도
-- 저장된 토큰을 다시 읽어 사용하지 않는다 (provider API 재호출 경로 없음). 평문 보관 자체가
-- 자격증명 누출 표면적을 키우므로 제거한다. provider 식별과 자동 로그인은 (provider, provider_uid)
-- 조합과 SocialAccount.created_at 으로 충분하다.
ALTER TABLE social_accounts DROP COLUMN access_token;
