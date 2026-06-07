-- 표준 회원 풀과 같은 규칙 (trim + lowercase) 으로 기존 사용자 식별자를 정규화한다.
-- 정규화 후 unique 제약을 다시 통과해야 하므로 위반 사례가 있으면 운영 데이터를 사전에 정리해야 한다.
-- 새 코드 (AuthService / OAuth2SignupService / AbstractOAuth2UserMapper) 도 같은 규칙으로
-- 입력을 정규화해 저장하므로 신규 가입자는 처음부터 정규화된 값으로 들어온다.

UPDATE users
   SET email    = LOWER(TRIM(email)),
       username = LOWER(TRIM(username));

-- 소셜 계정의 provider_email 도 표시·갱신 일관성을 위해 같은 규칙으로 정규화한다.
UPDATE social_accounts
   SET provider_email = LOWER(TRIM(provider_email))
 WHERE provider_email IS NOT NULL;
