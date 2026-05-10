# Echo Backend Entity 명세서

> **대상:** `develop` 브랜치 `backend/` 모듈의 JPA `@Entity`
> **스냅샷 일자:** 2026-05-07
> **위치:** `backend/src/main/java/com/capstoneecho/echo_back/app/**`
> **참고:** API 단의 도메인 흐름은 `API_SPEC.md`, 패키지/스택 개요는 `CLAUDE.md` 와 함께 본다.

---

## 1. 개요

### 1.1 도메인 그룹

```
Member          : User
Learning        : Track ─┬─ Script ─── LearningStep
                         └─ (Difficulty enum, StepKind enum)
Session         : Session ─── SessionSentence
Recording       : Recording (Script/LearningStep/Session/SessionSentence 를 ID로만 약결합)
Feedback        : PronunciationFeedback ─── PhonemeError
Ranking         : DemoRankingEntry (시연용 시드)
```

### 1.2 공통 규약

- 모든 엔티티는 Lombok `@Getter` + `@NoArgsConstructor(access = PROTECTED)` 패턴.
- PK 는 일관되게 `Long id` + `GenerationType.IDENTITY`.
- 타임스탬프는 `Instant` 타입이며, `@PrePersist` / `@PreUpdate` 훅에서 직접 채운다(`@CreatedDate` / `@LastModifiedDate` Auditing 은 사용하지 않음).
- 객체 그래프 fetch 전략은 명시된 곳 모두 `FetchType.LAZY`.
- 비밀번호는 `User.passwordHash` 필드에 BCrypt 해시 형태로 저장.
- 일부 도메인은 다른 엔티티를 `@ManyToOne` 이 아닌 `Long` ID 로만 참조한다(약결합). 자세한 목록은 §3.

---

## 2. 엔티티 상세

### 2.1 `User` — 회원 / 학습 통계 캐시 보유자

- **테이블:** `users`
- **유니크 제약:** `uk_users_username` (`username`), `uk_users_email` (`email`)
- **요약:** 인증 + 학습 통계 캐시를 보유하는 사용자. `streak`/`exp` 는 학습 기록 변경 시 갱신되며, 학습 메인 화면 헤더에서 즉시 노출된다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| username | username | varchar(50) | NOT NULL | 유니크 |
| email | email | varchar(100) | NOT NULL | 유니크 |
| passwordHash | password_hash | varchar(100) | NOT NULL | BCrypt 해시 |
| nickname | nickname | varchar(30) | NOT NULL | `changeNickname` 로만 갱신 |
| streak | streak | int | NOT NULL | `recordCompletion` 에서 +1 또는 1 리셋 |
| exp | exp | int | NOT NULL | 챕터 완료 시 가산 |
| lastStudyAt | last_study_at | Instant | NULL | streak 정책 기준점 |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |
| updatedAt | updated_at | Instant | NOT NULL | `@PreUpdate` |

- **연관관계:** 없음(다른 도메인은 `userId` Long 으로만 약결합).
- **라이프사이클:** `@PrePersist onCreate()` (createdAt/updatedAt 동시 세팅), `@PreUpdate onUpdate()` (updatedAt 갱신).
- **도메인 메서드:**
  - `static User create(username, email, passwordHash, nickname)` — 신규 사용자, streak/exp = 0.
  - `void changeNickname(String)` — 빈 입력 무시, 30자 초과는 절단.
  - `void recordCompletion(int expReward, ZoneId zone)` — 같은 날이면 streak 유지, 어제면 +1, 그 외/첫 학습이면 1로 리셋. `lastStudyAt = now`, `exp += expReward`.

### 2.2 `Track` — 학습 코스 최상위

- **테이블:** `tracks`
- **요약:** 학습 코스의 최상위 단위. 한 트랙은 순서 있는 챕터(`Script`) 들의 묶음.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| title | title | varchar(100) | NOT NULL | |
| description | description | TEXT | NULL | |
| displayOrder | display_order | int | NOT NULL | 작은 값이 먼저 노출 |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:** 없음(역방향 `Script.track` 만 존재).
- **도메인 메서드:** `static Track create(title, description, displayOrder)`.

### 2.3 `Script` — 트랙 안의 챕터

- **테이블:** `scripts`
- **인덱스:** `ix_scripts_track` (`track_id`, `chapter_order`)
- **요약:** 트랙 안의 한 챕터. `preset=true` 면 시드 챕터, false 면 사용자가 만든 자유 스크립트.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| track | track_id | FK → tracks.id | NULL | `@ManyToOne(LAZY)` |
| chapterOrder | chapter_order | Integer | NULL | 자유 스크립트는 null |
| title | title | varchar(255) | NOT NULL | |
| content | content | TEXT | NOT NULL | |
| difficulty | difficulty | varchar(16) | NULL | enum `Difficulty`, EnumType.STRING |
| preset | is_preset | boolean | NOT NULL | |
| practiceWord | practice_word | varchar(100) | NULL | 종합 피드백 권장 재연습 단어. 비어 있으면 `PracticeWordResolver` 가 약점 음소로 추정. |
| masteryBadgeName | mastery_badge_name | varchar(50) | NULL | 챕터 마스터 시 부여 배지명. 잰말놀이류는 비움. |
| createdAt | created_at | Instant | updatable=false | `@PrePersist` (이미 있으면 유지) |

- **연관관계:**
  - `track : Track` — `@ManyToOne(LAZY)`, FK `track_id`.
- **도메인 메서드:**
  - `static Script createChapter(track, chapterOrder, title, content, difficulty, practiceWord, masteryBadgeName)` — preset = true.
  - `static Script createStandalone(title, content, difficulty)` — preset = false.

### 2.4 `LearningStep` — 챕터 안의 학습 단계

- **테이블:** `learning_steps`
- **요약:** 한 `Script`(학습 unit) 안의 순서 있는 단계. `INTRO` 는 안내문만, `RECORD` 는 발음할 단어/문장을 함께 보유. **정답 음소는 도메인이 보관하지 않고 녹음 시점에 모델 서버 G2P 로 즉석 산출된다.**

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| script | script_id | FK → scripts.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| orderIndex | order_index | int | NOT NULL | |
| kind | kind | varchar(16) | NOT NULL | enum `StepKind`, EnumType.STRING |
| prompt | prompt | TEXT | NOT NULL | |
| targetText | target_text | TEXT | NULL | RECORD 단계만 채워짐 |

- **연관관계:**
  - `script : Script` — `@ManyToOne(LAZY, optional=false)`, FK `script_id` NOT NULL.
- **도메인 메서드:**
  - `static LearningStep intro(script, orderIndex, prompt)` — `INTRO`, targetText=null.
  - `static LearningStep record(script, orderIndex, prompt, targetText)` — `RECORD`.

### 2.5 `Session` — 사용자 맞춤 학습 세션

- **테이블:** `sessions`
- **인덱스:** `ix_sessions_user` (`user_id`)
- **요약:** 사용자가 직접 대본을 입력해 만드는 맞춤 학습 세션. `scriptText` 가 채워지면 녹음/피드백으로 넘어간다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| userId | user_id | Long | NOT NULL | `User` 와 약결합 (FK 미선언) |
| title | title | varchar(100) | NOT NULL | |
| scriptText | script_text | TEXT | NOT NULL | 초기값 빈 문자열 |
| favorite | favorite | boolean | NOT NULL | 즐겨찾기 정렬 키 |
| sentences | (역방향) | List\<SessionSentence\> | — | `@OneToMany(mappedBy="session", cascade=ALL, orphanRemoval=true)` + `@OrderBy("sentenceIndex ASC")` |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |
| updatedAt | updated_at | Instant | NOT NULL | `@PreUpdate` |

- **연관관계:**
  - `sentences : List<SessionSentence>` — `@OneToMany`, mappedBy=`session`, cascade=ALL, orphanRemoval=true.
- **도메인 메서드:**
  - `static Session create(userId, title)` — scriptText 빈 문자열, favorite=false.
  - `void rename(String title)` — null/blank 무시.
  - `void setFavorite(boolean)`.
  - `void updateScript(String scriptText, List<String> sentenceTexts)` — scriptText null 이면 무변경. 비-null 이면 sentences 를 새 리스트로 통째로 교체(orphanRemoval 동작).

### 2.6 `SessionSentence` — 세션 안의 문장 한 조각

- **테이블:** `session_sentences`
- **인덱스:** `ix_session_sentences_session` (`session_id`, `sentence_index`)
- **요약:** `Session` 안의 한 학습 문장. `SentenceSplitter` 로 분할된 결과 한 조각이며, `sentenceIndex` 가 같은 세션 내 순서를 결정한다. `Recording.sessionSentenceId` 가 이 엔티티의 id 를 참조해 어떤 문장에 대한 녹음인지 식별한다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| session | session_id | FK → sessions.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| sentenceIndex | sentence_index | int | NOT NULL | |
| text | text | TEXT | NOT NULL | |

- **연관관계:** `session : Session` — `@ManyToOne(LAZY, optional=false)`.
- **도메인 메서드:** `static SessionSentence of(session, sentenceIndex, text)`.

### 2.7 `Recording` — 한 건의 사용자 녹음 메타

- **테이블:** `recordings`
- **인덱스:** `ix_recordings_user` (`user_id`), `ix_recordings_script` (`script_id`), `ix_recordings_session` (`session_id`)
- **요약:** 한 건의 사용자 녹음 메타. 참조는 `scriptId`/`sessionId` 중 하나만 사용한다. `stepId` 는 정해진 학습 단계(추천 학습)일 때만. `audioPath` 는 디스크 저장 경로, `perceived`/`canonical`/`peakSoftmax` 는 모델 응답 캐시, `stepScore` 는 `ScoringPolicy` 로 계산한 단계 점수(0~100).

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| userId | user_id | Long | NOT NULL | `User` 와 약결합 |
| scriptId | script_id | Long | NULL | `Script` 와 약결합 |
| sessionId | session_id | Long | NULL | `Session` 과 약결합 |
| stepId | step_id | Long | NULL | `LearningStep` 과 약결합 |
| sessionSentenceId | session_sentence_id | Long | NULL | `SessionSentence` 와 약결합. 추천 학습의 step_id 와 동등 위치의 식별자. |
| audioPath | audio_path | varchar(500) | NOT NULL | 로컬 디스크 저장 경로 |
| durationSec | duration_sec | Double | NULL | |
| perceived | perceived | TEXT | NULL | 모델 응답 캐시 |
| canonical | canonical | TEXT | NULL | 모델 응답 캐시 |
| peakSoftmax | peak_softmax | TEXT | NULL | 모델 응답 캐시 |
| stepScore | step_score | Double | NULL | `ScoringPolicy` 결과 (0~100) |
| guidanceKr | guidance_kr | TEXT | NULL | 녹음 직후 채팅에 노출되는 한국어 한 줄 가이드 |
| errorsJson | errors_json | TEXT | NULL | `ModelAnalyzeResponse.errors` JSON 직렬화. `FeedbackService` 가 unit 종합 시 음소 빈도 집계 |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:** 없음 (모두 Long 약결합). 본 클래스 안의 `record AnalysisOutcome(...)` 은 엔티티가 아닌 결과 묶음 값객체로, JPA 매핑 대상 아님.
- **도메인 메서드:**
  - `static Recording forScriptStep(userId, scriptId, stepId, audioPath)` — 추천 학습 트랙의 한 step 에 대한 녹음.
  - `static Recording forSessionSentence(userId, sessionId, sessionSentenceId, audioPath)` — 사용자 맞춤 세션의 한 문장에 대한 녹음.
  - `static Recording forSessionFreeForm(userId, sessionId, audioPath)` — 사용자 맞춤 세션을 한 호흡으로 통째 녹음(sentence 분리 없음).
  - `void applyAnalysis(AnalysisOutcome outcome)` — perceived/canonical/peakSoftmax/errorsJson/guidanceKr/durationSec/stepScore 한 번에 반영.

### 2.8 `PronunciationFeedback` — 종합 피드백 한 건

- **테이블:** `pronunciation_feedbacks`
- **인덱스:** `ix_feedbacks_user` (`user_id`)
- **요약:** 한 챕터 또는 세션을 끝냈을 때 만들어지는 종합 피드백 한 건. `accuracy` 는 step 점수 평균(0~100), `weakPhoneme` 는 가장 자주 틀린 음소.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| userId | user_id | Long | NOT NULL | `User` 와 약결합 |
| scriptId | script_id | Long | NULL | `Script` 와 약결합 |
| sessionId | session_id | Long | NULL | `Session` 과 약결합 |
| title | title | varchar(200) | NOT NULL | |
| accuracy | accuracy | double | NOT NULL | step 점수 평균 |
| weakPhoneme | weak_phoneme | varchar(32) | NULL | |
| practiceWord | practice_word | varchar(100) | NULL | |
| guidanceKr | guidance_kr | TEXT | NULL | |
| completed | completed | boolean | NOT NULL | 보상이 한 번 적용된 피드백인지. EXP 중복 가산 방지 플래그. |
| errors | (역방향) | List\<PhonemeError\> | — | `@OneToMany(mappedBy="feedback", cascade=ALL, orphanRemoval=true)` |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:**
  - `errors : List<PhonemeError>` — `@OneToMany`, mappedBy=`feedback`, cascade=ALL, orphanRemoval=true.
- **도메인 메서드:**
  - `static PronunciationFeedback create(userId, scriptId, sessionId, title, accuracy, weakPhoneme, practiceWord, guidanceKr)`.
  - `void addError(PhonemeError)` — 역방향 attach 후 컬렉션에 추가.
  - `boolean markCompleted()` — 처음 완료 처리할 때만 true. 호출자는 이 값으로 보상 가산 여부 결정.

### 2.9 `PhonemeError` — 피드백 안의 단일 오류 항목

- **테이블:** `phoneme_errors`
- **요약:** 한 `PronunciationFeedback` 안의 단일 오류 항목. `op` 는 `substitution` / `insertion` / `deletion` / `correct`.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| feedback | feedback_id | FK → pronunciation_feedbacks.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| op | op | varchar(16) | NOT NULL | substitution / insertion / deletion / correct |
| canonical | canonical | varchar(32) | NULL | |
| perceived | perceived | varchar(32) | NULL | |
| canonicalIndex | canonical_index | Integer | NULL | |

- **연관관계:** `feedback : PronunciationFeedback` — `@ManyToOne(LAZY, optional=false)`, FK `feedback_id` NOT NULL.
- **도메인 메서드:**
  - `static PhonemeError of(op, canonical, perceived, canonicalIndex)`.
  - 패키지 가시성 `void attachTo(PronunciationFeedback)` — `addError` 가 역방향 세팅용으로 호출.

### 2.10 `DemoRankingEntry` — 시연용 랭킹 시드 한 줄

- **테이블:** `demo_ranking_entries`
- **요약:** 시연 단계에서 랭킹 화면을 풍성하게 보여주기 위한 가짜 사용자 한 줄. 실제 `PronunciationFeedback` 누적이 충분해질 때까지의 임시 보조 데이터이며, 운영 단계에선 행 전체를 비우면 그대로 사라진다. **코드가 아닌 DB 시드로 관리되어 추가/조정에 재배포가 필요 없다.**

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| nickname | nickname | varchar(30) | NOT NULL | |
| accuracy | accuracy | double | NOT NULL | |

- **연관관계:** 없음.
- **도메인 메서드:** `static DemoRankingEntry of(nickname, accuracy)`.

---

## 3. 연관관계 다이어그램

### 3.1 강결합 (`@ManyToOne` / `@OneToMany`)

```
Track (1) ◄─ @ManyToOne ─ Script (N)
Script (1) ◄─ @ManyToOne(optional=false) ─ LearningStep (N)
Session (1) ──────── @OneToMany cascade=ALL, orphanRemoval=true, @OrderBy(sentenceIndex) ──────► SessionSentence (N)
                                                  │ (역방향 @ManyToOne(optional=false))
PronunciationFeedback (1) ── @OneToMany cascade=ALL, orphanRemoval=true ────► PhonemeError (N)
                                                  │ (역방향 @ManyToOne(optional=false))
```

### 3.2 약결합 (Long ID 컬럼만, FK/`@ManyToOne` 미선언)

| From → To | 컬럼 | 코드 주석에 명시된 사유 |
| --- | --- | --- |
| `Session.userId` → `User` | `user_id` | 이유 알 수 없음 |
| `Recording.userId` → `User` | `user_id` | 이유 알 수 없음 |
| `Recording.scriptId` → `Script` | `script_id` | 이유 알 수 없음 (클래스 주석은 "셋 중 하나만 사용"이라는 사용 규칙만 설명, FK 미선언 사유는 없음) |
| `Recording.sessionId` → `Session` | `session_id` | 이유 알 수 없음 (동상) |
| `Recording.stepId` → `LearningStep` | `step_id` | 이유 알 수 없음 (동상) |
| `Recording.sessionSentenceId` → `SessionSentence` | `session_sentence_id` | 이유 알 수 없음 (필드 주석은 의미만 설명) |
| `PronunciationFeedback.userId` → `User` | `user_id` | 이유 알 수 없음 |
| `PronunciationFeedback.scriptId` → `Script` | `script_id` | 이유 알 수 없음 |
| `PronunciationFeedback.sessionId` → `Session` | `session_id` | 이유 알 수 없음 |

> 위 "사유" 칸은 **소스 주석에 명시된 경우에만** 인용한다. 새 주석이 추가되면 본 문서를 갱신할 것.

---

## 4. 인덱스 / 유니크 제약 요약

| 테이블 | 종류 | 이름 | 컬럼 |
| --- | --- | --- | --- |
| `users` | UNIQUE | `uk_users_username` | username |
| `users` | UNIQUE | `uk_users_email` | email |
| `scripts` | INDEX | `ix_scripts_track` | track_id, chapter_order |
| `sessions` | INDEX | `ix_sessions_user` | user_id |
| `session_sentences` | INDEX | `ix_session_sentences_session` | session_id, sentence_index |
| `recordings` | INDEX | `ix_recordings_user` | user_id |
| `recordings` | INDEX | `ix_recordings_script` | script_id |
| `recordings` | INDEX | `ix_recordings_session` | session_id |
| `pronunciation_feedbacks` | INDEX | `ix_feedbacks_user` | user_id |

> `tracks`, `learning_steps`, `phoneme_errors`, `demo_ranking_entries` 는 추가 인덱스/유니크 제약을 선언하지 않는다(PK 만 존재).

---

## 부록 A. 도메인 enum

- `app/script/Difficulty` — `EASY`, `MEDIUM`, `HARD`. `Script.difficulty` 에서 `EnumType.STRING` 으로 매핑.
- `app/learning/StepKind` — `INTRO`(안내, 녹음 없음), `RECORD`(사용자가 발음/녹음 업로드 필요). `LearningStep.kind` 에서 `EnumType.STRING` 으로 매핑.

## 부록 B. 주의점

- `User.streak` 의 7 캡 등 통계 정책은 엔티티가 아닌 서비스 계층(예: `StatsService`, `BadgePolicy`)에 존재. 본 문서의 컬럼 설명은 저장 정의에 한정한다.
- `Recording.errors_json` 은 `ModelAnalyzeResponse.errors` 의 JSON 문자열 캐시(JSON-as-text). 정규화된 별도 테이블이 아니다.
- `DemoRankingEntry` 는 시연 단계의 시드 데이터를 위한 보조 테이블. 운영 단계에선 비우면 자연 소멸한다.
- 본 문서는 `develop` HEAD 의 스냅샷이며, develop 의 엔티티가 변경되면 별도 PR 로 본 문서도 갱신해야 한다.
