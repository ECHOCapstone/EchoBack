# echo-back-app

## 패키지

`com.capstoneecho.echo_back.app` 하위 도메인별 분리.

| 도메인 | 책임 |
|---|---|
| `auth`, `member` | 회원가입·로그인·JWT, `/api/members/me` |
| `script`, `learning` | 추천 학습 unit · LearningStep |
| `session` | 사용자 맞춤 학습 세션 CRUD |
| `recording`, `feedback` | 녹음 업로드 + step별 즉시 가이드 + unit 종합 피드백 |
| `stats`, `ranking`, `tts` | 통계·랭킹·TTS 프록시 |
| `phoneme` | 음소 조음 이미지 업로드·서빙 |
| `admin`, `settings` | 관리자 API(트랙·스크립트·프롬프트·음소·LLM·설정), 런타임 설정 오버라이드 |
| `common`, `jwt` | 응답 envelope, 에러 카탈로그, JWT 필터 |
| `seed` | 시드 데이터 초기화 |

## 실행

Java 25 · Gradle. base 설정에는 datasource·활성 프로파일이 없으므로 **프로파일을 반드시 지정**한다.
외부 DB 없이 띄우려면 `dev`(H2 인메모리), MySQL 로 띄우려면 `local` 을 쓴다.

```bash
cp .env.example .env.local   # 값 채우기
source .env.local            # Spring Boot 는 .env 를 자동으로 읽지 않으므로 쉘에 올린다

# (1) H2 인메모리 — 외부 DB 불필요, 가장 간단
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# (2) MySQL — localhost:3306 에 DB·계정 echo / echo 준비 후. Flyway 가 스키마를 마이그레이션
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 포트 **8080**. 부팅 시 시드 데이터(`tracks.json` 등)가 비어 있으면 자동 주입된다.

## 환경 변수

`source .env.local` 로 쉘에 올린 뒤 실행한다 (Spring Boot 가 `.env` 를 자동으로 읽지 않는다).
`application.yaml` 의 외부 의존성은 모두 `${VAR_NAME:default}` 형태다. 키 목록은 [.env.example](.env.example) 참고.

- **GOOGLE_CLIENT_ID/SECRET, KAKAO_REST_API_KEY/SECRET** — OAuth2 client 자격증명. **비어 있으면 부팅이 실패**하므로 소셜 로그인을 안 쓰더라도 더미 값이라도 채운다.
- **APP_JWT_SECRET** — JWT 서명 키(HS256, 32바이트+). dev/local 프로파일은 기본값이 있어 생략 가능.
- **GEMINI_API_KEY + APP_LLM_PROVIDER=gemini** — 실제 Gemini 피드백을 쓸 때만. 기본은 외부 호출 없는 rule-based.
- **APP_ADMIN_BOOTSTRAP_USERNAME** — 이 username 계정을 부팅 시 관리자(ROLE_ADMIN)로 승격한다.

## 응답 envelope

```
{ "success": true,  "data": { ... } }
{ "success": false, "error": { "code": "...", "message": "..." } }
```
