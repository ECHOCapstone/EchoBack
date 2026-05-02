# echo-back-app

ECHO 백엔드 (영환). 지용님 root 트리(`src/main/java/com/capstoneecho/echo_back/`) 와 분리된
독립 Gradle 프로젝트로, 프론트엔드 + 모델 서버를 끝단까지 연결하는 동작 가능 구현이다.

## 패키지

`com.capstoneecho.echo_back.app` 하위 도메인별 분리.

| 도메인 | 책임 |
|---|---|
| `auth`, `member` | 회원가입·로그인·JWT, `/api/members/me` |
| `script`, `learning` | 추천 학습 unit · LearningStep |
| `session` | 사용자 맞춤 학습 세션 CRUD |
| `recording`, `feedback` | 녹음 업로드 + step별 즉시 가이드 + unit 종합 피드백 |
| `stats`, `ranking`, `tts` | 통계·랭킹·TTS 프록시 |
| `common`, `jwt` | 응답 envelope, 에러 카탈로그, JWT 필터 |
| `seed` | 시드 데이터 초기화 |

## 실행

```
cd backend
cp .env.example .env       # 비밀값 채우기
./gradlew bootRun
```

기본 포트 8080. H2 in-memory 라 부팅 시 시드 데이터가 자동 주입된다.

## 환경 변수

`.env` 가 backend root 에 있으면 Spring Boot 가 properties 형식으로 자동 로드한다.
`application.yaml` 의 모든 외부 의존성은 `${VAR_NAME:default}` 형태로 노출되어 있다.
키 목록은 [.env.example](.env.example) 참고.

## 응답 envelope

```
{ "success": true,  "data": { ... } }
{ "success": false, "error": { "code": "...", "message": "..." } }
```
