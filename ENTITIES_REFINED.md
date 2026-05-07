# Echo Backend Entity 명세서

## 0. 메타

- **대상:** 본 프로젝트 backend JPA `@Entity` 전체.
- **엔트리 포인트:** `src/main/java/com/capstoneecho/echo_back/EchoBackApplication.java`
- **루트 패키지:** `com.capstoneecho.echo_back` (Spring Boot 4.0.5, Java 25)
- **소스 루트:** `src/main/java/com/capstoneecho/echo_back/**` 아래에 feature 별 sub-package 로 배치한다 (`member`, `track`, `script`, `learning`, `session`, `recording`, `feedback`, `ranking`).
- **글로벌 공용 컴포넌트:** 공통 예외, 공유 설정, 보안 설정 등은 `com.capstoneecho.echo_back.global` 하위에 둔다.
- **작성일:** 2026-05-07.

---

## 1. 개요

### 1.1 도메인 그룹

```
Member        : User
Learning      : Track ─ Script ─ LearningStep
Session       : Session ─ SessionSentence
Recording     : Recording  (User / Script / LearningStep / Session / SessionSentence 와 직접 관계)
Feedback      : PronunciationFeedback ─ PhonemeError
Ranking       : DemoRankingEntry
```

### 1.2 공통 규약

- 모든 엔티티는 Lombok `@Getter` + `@NoArgsConstructor(access = PROTECTED)` 패턴.
- PK 는 일관되게 `Long id` + `GenerationType.IDENTITY`.
- 타임스탬프는 `Instant` 타입이며, `@PrePersist` / `@PreUpdate` 훅에서 직접 채운다.
- 객체 그래프 fetch 전략은 모든 `@ManyToOne` / `@OneToMany` 에서 `LAZY` 를 기본으로 한다.
- 비밀번호는 `User.passwordHash` 필드에 BCrypt 해시 형태로 저장한다.
- **사용자 소유 데이터의 모든 참조는 직접 엔티티 관계(`@ManyToOne` / `@OneToMany`)로 표현한다.** Long ID 만으로 약결합하지 않는다.
- **데이터 무결성은 데이터베이스 레벨(FK, NOT NULL, CHECK, 원자 UPDATE)로 우선 강제한다.** 도메인 메서드의 가드는 그 위에 동일 규칙을 한 번 더 표현하는 보조 수단이다.

---

## 2. 엔티티 상세

엔티티 순서: User → Track → Script → LearningStep → Session → SessionSentence → Recording → PronunciationFeedback → PhonemeError → DemoRankingEntry.

### 2.1 `User` — 회원 / 학습 통계 캐시 보유자

- **테이블:** `users`
- **유니크 제약:** `uk_users_username` (`username`), `uk_users_email` (`email`)
- **요약:** 인증 + 학습 통계 캐시를 보유하는 사용자. `streak`/`exp` 는 학습 완료 시 갱신되며, 학습 메인 화면 헤더에서 즉시 노출된다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| username | username | varchar(50) | NOT NULL | 유니크 |
| email | email | varchar(100) | NOT NULL | 유니크 |
| passwordHash | password_hash | varchar(100) | NOT NULL | BCrypt 해시 |
| nickname | nickname | varchar(30) | NOT NULL | `changeNickname` 으로만 갱신 |
| streak | streak | int | NOT NULL | `recordCompletion` 정책에 따라 +1 또는 1 리셋 |
| exp | exp | int | NOT NULL | 챕터 완료 시 가산 |
| lastStudyAt | last_study_at | Instant | NULL | streak 정책 기준점 |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |
| updatedAt | updated_at | Instant | NOT NULL | `@PreUpdate` |

- **연관관계:** 없음 (역방향은 `Session.user`, `Recording.user`, `PronunciationFeedback.user`).
- **라이프사이클:** `@PrePersist onCreate()` (createdAt/updatedAt 동시 세팅), `@PreUpdate onUpdate()` (updatedAt 갱신).
- **도메인 메서드:**
  - `static User create(username, email, passwordHash, nickname)` — 신규 사용자, streak/exp = 0.
  - `void changeNickname(String)` — 빈 입력 무시, 30자 초과는 절단.
  - `void recordCompletion(int expReward, ZoneId zone)` — 같은 날이면 streak 유지, 어제면 +1, 그 외/첫 학습이면 1로 리셋. `lastStudyAt = now`, `exp += expReward`.

### 2.2 `Track` — 학습 코스 최상위

- **테이블:** `tracks`
- **요약:** 학습 코스의 최상위 단위. 한 트랙은 순서 있는 챕터(`Script`)들의 묶음.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| title | title | varchar(100) | NOT NULL | |
| description | description | TEXT | NULL | |
| displayOrder | display_order | int | NOT NULL | 작은 값이 먼저 노출 |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:** 없음 (역방향은 `Script.track`).
- **도메인 메서드:** `static Track create(title, description, displayOrder)`.

### 2.3 `Script` — 트랙 안의 챕터

- **테이블:** `scripts`
- **인덱스:** `ix_scripts_track` (`track_id`, `chapter_order`) — 다중 컬럼 인덱스이므로 `@Table(indexes=...)` 로 명시 선언한다.
- **요약:** 트랙 안의 한 챕터. `preset=true` 면 시드 챕터, false 면 사용자가 만든 자유 스크립트.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| track | track_id | FK → tracks.id | NULL | `@ManyToOne(LAZY)` |
| chapterOrder | chapter_order | Integer | NULL | 자유 스크립트는 null |
| title | title | varchar(255) | NOT NULL | |
| content | content | TEXT | NOT NULL | |
| difficulty | difficulty | varchar(16) | NULL | enum `Difficulty`, `EnumType.STRING` |
| preset | is_preset | boolean | NOT NULL | |
| practiceWord | practice_word | varchar(100) | NULL | 종합 피드백 권장 재연습 단어. 비어 있으면 `PracticeWordResolver` 가 약점 음소로 추정. |
| masteryBadgeName | mastery_badge_name | varchar(50) | NULL | 챕터 마스터 시 부여 배지명. 잰말놀이류는 비움. |
| createdAt | created_at | Instant | updatable=false | `@PrePersist` (이미 있으면 유지) |

- **연관관계:** `track : Track` — `@ManyToOne(LAZY)`, FK `track_id`.
- **도메인 메서드:**
  - `static Script createChapter(track, chapterOrder, title, content, difficulty, practiceWord, masteryBadgeName)` — preset = true.
  - `static Script createStandalone(title, content, difficulty)` — preset = false.

### 2.4 `LearningStep` — 챕터 안의 학습 단계

- **테이블:** `learning_steps`
- **요약:** 한 `Script`(학습 unit) 안의 순서 있는 단계. `INTRO` 는 안내문만, `RECORD` 는 발음할 단어/문장을 함께 보유한다. 정답 음소는 도메인이 보관하지 않고 녹음 시점에 모델 서버 G2P 로 즉석 산출된다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| script | script_id | FK → scripts.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| orderIndex | order_index | int | NOT NULL | |
| kind | kind | varchar(16) | NOT NULL | enum `StepKind`, `EnumType.STRING` |
| prompt | prompt | TEXT | NOT NULL | |
| targetText | target_text | TEXT | NULL | RECORD 단계만 채워짐 |

- **연관관계:** `script : Script` — `@ManyToOne(LAZY, optional=false)`, FK `script_id` NOT NULL.
- **도메인 메서드:**
  - `static LearningStep intro(script, orderIndex, prompt)` — `INTRO`, targetText=null.
  - `static LearningStep record(script, orderIndex, prompt, targetText)` — `RECORD`.

### 2.5 `Session` — 사용자 맞춤 학습 세션

- **테이블:** `sessions`
- **인덱스:** `user_id` 인덱스는 MySQL FK 보조 인덱스로 자동 생성되므로 엔티티 코드에 `@Index` 로 직접 선언하지 않는다.
- **요약:** 사용자가 직접 대본을 입력해 만드는 맞춤 학습 세션. `scriptText` 가 채워지면 녹음/피드백으로 넘어간다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| user | user_id | FK → users.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| title | title | varchar(100) | NOT NULL | |
| scriptText | script_text | TEXT | NOT NULL | 초기값 빈 문자열 |
| favorite | favorite | boolean | NOT NULL | 즐겨찾기 정렬 키 |
| sentences | (역방향) | List\<SessionSentence\> | — | `@OneToMany(mappedBy="session", cascade=ALL, orphanRemoval=true)` + `@OrderBy("sentenceIndex ASC")` |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |
| updatedAt | updated_at | Instant | NOT NULL | `@PreUpdate` |

- **연관관계:**
  - `user : User` — `@ManyToOne(LAZY, optional=false)`, FK `user_id` NOT NULL.
  - `sentences : List<SessionSentence>` — `@OneToMany`, mappedBy=`session`, cascade=ALL, orphanRemoval=true.
- **라이프사이클:** `@PrePersist`, `@PreUpdate` (updatedAt 갱신).
- **도메인 메서드:**
  - `static Session create(User user, String title)` — scriptText 빈 문자열, favorite=false.
  - `void rename(String title)` — null/blank 무시.
  - `void setFavorite(boolean)`.
  - `void updateScript(String scriptText, List<String> sentenceTexts)` — scriptText null 이면 무동작. 비-null 이면 sentences 컬렉션을 새 리스트로 통째 교체(orphanRemoval 동작).

### 2.6 `SessionSentence` — 세션 안의 문장 한 조각

- **테이블:** `session_sentences`
- **인덱스:** `ix_session_sentences_session` (`session_id`, `sentence_index`) — 다중 컬럼 인덱스이므로 `@Table(indexes=...)` 로 명시 선언한다.
- **요약:** `Session` 안의 한 학습 문장. `SentenceSplitter` 로 분할된 결과 한 조각이며, `sentenceIndex` 가 같은 세션 내 순서를 결정한다.

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
- **인덱스:** `user_id` / `script_id` / `session_id` / `step_id` / `session_sentence_id` 인덱스는 모두 MySQL FK 보조 인덱스로 자동 생성되므로 엔티티 코드에 `@Index` 로 직접 선언하지 않는다.
- **요약:** 한 건의 사용자 녹음 메타. 추천 학습 트랙(Script + LearningStep) 또는 사용자 맞춤 세션(Session [+ SessionSentence]) 중 한 흐름에 속한다. `audioPath` 는 디스크 저장 경로, `perceived`/`canonical`/`peakSoftmax`/`errorsJson`/`guidanceKr` 는 모델 응답 캐시, `stepScore` 는 `ScoringPolicy` 로 계산한 단계 점수(0~100).

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| user | user_id | FK → users.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| script | script_id | FK → scripts.id | NULL | `@ManyToOne(LAZY)` |
| session | session_id | FK → sessions.id | NULL | `@ManyToOne(LAZY)` |
| step | step_id | FK → learning_steps.id | NULL | `@ManyToOne(LAZY)` |
| sessionSentence | session_sentence_id | FK → session_sentences.id | NULL | `@ManyToOne(LAZY)` + `@OnDelete(action = OnDeleteAction.SET_NULL)` |
| audioPath | audio_path | varchar(500) | NOT NULL | 로컬 디스크 저장 경로 |
| targetTextSnapshot | target_text_snapshot | TEXT | NULL | 녹음 생성 시점의 학습 대상 문장/단어를 그대로 복사한 immutable 캐시. 사용자가 세션 대본을 편집해 SessionSentence 가 사라지더라도 어떤 문장에 대한 녹음이었는지를 자기완결적으로 보존한다. |
| durationSec | duration_sec | Double | NULL | |
| perceived | perceived | TEXT | NULL | 모델 응답 캐시 |
| canonical | canonical | TEXT | NULL | 모델 응답 캐시 |
| peakSoftmax | peak_softmax | TEXT | NULL | 모델 응답 캐시 |
| stepScore | step_score | Double | NULL | `ScoringPolicy` 결과 (0~100) |
| guidanceKr | guidance_kr | TEXT | NULL | 녹음 직후 채팅 흐름에 노출되는 한국어 한 줄 가이드 |
| errorsJson | errors_json | TEXT | NULL | `ModelAnalyzeResponse.errors` JSON 직렬화. `FeedbackService` 가 unit 종합 시 음소 빈도 집계에 사용. |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:**
  - `user : User` — `@ManyToOne(LAZY, optional=false)`, FK `user_id` NOT NULL.
  - `script : Script` — `@ManyToOne(LAZY)`, nullable.
  - `session : Session` — `@ManyToOne(LAZY)`, nullable.
  - `step : LearningStep` — `@ManyToOne(LAZY)`, nullable.
  - `sessionSentence : SessionSentence` — `@ManyToOne(LAZY)` + `@OnDelete(action = OnDeleteAction.SET_NULL)`, nullable. SessionSentence 가 삭제되면 본 컬럼은 NULL 로 끊어지고, `targetTextSnapshot` 으로 의미가 보존된다.
- **정합성 규칙 (DB CHECK + 도메인 정적 팩토리 동시 강제):**
  - `(script_id IS NULL) <> (session_id IS NULL)` — script 와 session 은 정확히 하나만 NOT NULL.
  - `step_id IS NULL OR script_id IS NOT NULL` — step 이 있으면 그 step 이 속한 script 도 동시에 지정되어야 한다.
  - `session_sentence_id IS NULL OR session_id IS NOT NULL` — sentence 가 있으면 그 sentence 가 속한 session 도 동시에 지정되어야 한다.
  - 위 3개 조건은 Hibernate 의 `@org.hibernate.annotations.Check` (또는 `@Checks` 묶음) 로 엔티티 클래스에 선언하여 DDL 레벨 CHECK 제약으로 생성한다.
- **도메인 메서드:**
  - `static Recording forScriptStep(User user, Script script, LearningStep step, String audioPath, String targetTextSnapshot)` — 추천 학습 트랙의 한 step 에 대한 녹음.
  - `static Recording forSessionSentence(User user, Session session, SessionSentence sentence, String audioPath, String targetTextSnapshot)` — 사용자 맞춤 세션의 한 문장에 대한 녹음.
  - `static Recording forSessionFreeForm(User user, Session session, String audioPath, String targetTextSnapshot)` — 사용자 맞춤 세션을 한 호흡으로 통째 녹음(sentence 분리 없음).
  - `void applyAnalysis(AnalysisOutcome outcome)` — perceived/canonical/peakSoftmax/errorsJson/guidanceKr/durationSec/stepScore 한 번에 반영.
- **값 객체:** 본 클래스 안의 `record AnalysisOutcome(perceivedJoined, canonicalJoined, peakSoftmaxJoined, errorsJson, guidanceKr, durationSec, stepScore)` 은 분석 결과를 한 번에 들고 다니는 묶음 값이며 JPA 매핑 대상 아님.

### 2.8 `PronunciationFeedback` — 종합 피드백 한 건

- **테이블:** `pronunciation_feedbacks`
- **인덱스:** `user_id` / `script_id` / `session_id` 인덱스는 MySQL FK 보조 인덱스로 자동 생성되므로 엔티티 코드에 `@Index` 로 직접 선언하지 않는다.
- **요약:** 한 챕터 또는 한 세션을 끝냈을 때 만들어지는 종합 피드백 한 건. `accuracy` 는 step 점수 평균(0~100), `weakPhoneme` 는 가장 자주 틀린 음소.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| user | user_id | FK → users.id | NOT NULL | `@ManyToOne(LAZY, optional=false)` |
| script | script_id | FK → scripts.id | NULL | `@ManyToOne(LAZY)` |
| session | session_id | FK → sessions.id | NULL | `@ManyToOne(LAZY)` |
| title | title | varchar(200) | NOT NULL | |
| accuracy | accuracy | double | NOT NULL | step 점수 평균 |
| weakPhoneme | weak_phoneme | varchar(32) | NULL | |
| practiceWord | practice_word | varchar(100) | NULL | |
| guidanceKr | guidance_kr | TEXT | NULL | |
| completed | completed | boolean | NOT NULL | 보상이 한 번 적용되었는지. 원자 UPDATE 로만 true 로 전환된다. |
| errors | (역방향) | List\<PhonemeError\> | — | `@OneToMany(mappedBy="feedback", cascade=ALL, orphanRemoval=true)` |
| createdAt | created_at | Instant | NOT NULL, updatable=false | `@PrePersist` |

- **연관관계:**
  - `user : User` — `@ManyToOne(LAZY, optional=false)`.
  - `script : Script` — `@ManyToOne(LAZY)`, nullable.
  - `session : Session` — `@ManyToOne(LAZY)`, nullable.
  - `errors : List<PhonemeError>` — `@OneToMany`, mappedBy=`feedback`, cascade=ALL, orphanRemoval=true.
- **정합성 규칙:** `(script_id IS NULL) <> (session_id IS NULL)` — script 와 session 정확히 하나만 NOT NULL. Hibernate 의 `@org.hibernate.annotations.Check` 로 엔티티 클래스에 선언, DDL 레벨 CHECK 제약으로 생성한다. 도메인 정적 팩토리도 동일 규칙을 강제한다.
- **완료 보상의 idempotency:** `completed` 를 false → true 로 전환하는 경로는 **원자 UPDATE 한 가지뿐**이다. 엔티티에 `markCompleted()` 같은 read-modify-write 도메인 메서드는 두지 않는다 (보조 메서드를 노출하면 직접 호출 경로가 race 를 만든다). 보상 가산은 `FeedbackRepository.markCompletedAtomically(...)` 가 영향 행 1을 반환할 때에만 수행한다 (§5).
- **도메인 메서드:**
  - `static PronunciationFeedback create(User user, Script script, Session session, String title, double accuracy, String weakPhoneme, String practiceWord, String guidanceKr)` — 정합성 규칙(script XOR session)을 정적 팩토리에서 강제.
  - `void addError(PhonemeError)` — 역방향 attach 후 컬렉션 추가.

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
- **요약:** 시연 단계에서 랭킹 화면을 풍성하게 보여주기 위한 가짜 사용자 한 줄. 실제 `PronunciationFeedback` 누적이 충분해질 때까지의 임시 보조 데이터이며, 운영 단계에선 행 전체를 비우면 그대로 사라진다. 코드가 아닌 DB 시드로 관리되어 추가/조정에 재배포가 필요 없다.

| 필드 | DB 컬럼 | 타입 | nullable | 비고 |
| --- | --- | --- | --- | --- |
| id | id | Long | 자동 | PK |
| nickname | nickname | varchar(30) | NOT NULL | |
| accuracy | accuracy | double | NOT NULL | |

- **연관관계:** 없음.
- **도메인 메서드:** `static DemoRankingEntry of(nickname, accuracy)`.

---

## 3. 연관관계 다이어그램

모든 도메인 간 참조는 엔티티 관계로 표현된다.

```
User (1) ─┬─ (역방향) ─► Session (N)
          ├─ (역방향) ─► Recording (N)
          └─ (역방향) ─► PronunciationFeedback (N)

Track (1) ◄─ @ManyToOne ─ Script (N)
Script (1) ◄─ @ManyToOne(optional=false) ─ LearningStep (N)
Session (1) ──── @OneToMany cascade=ALL, orphanRemoval=true, @OrderBy(sentenceIndex) ────► SessionSentence (N)
                                                  │ (역방향 @ManyToOne(optional=false))
PronunciationFeedback (1) ─── @OneToMany cascade=ALL, orphanRemoval=true ───► PhonemeError (N)
                                                  │ (역방향 @ManyToOne(optional=false))

Recording ─ @ManyToOne(optional=false)            ─► User
Recording ─ @ManyToOne                             ─► Script           (nullable)
Recording ─ @ManyToOne                             ─► Session          (nullable)
Recording ─ @ManyToOne                             ─► LearningStep     (nullable)
Recording ─ @ManyToOne + @OnDelete(SET_NULL)       ─► SessionSentence  (nullable)

PronunciationFeedback ─ @ManyToOne(optional=false) ─► User
PronunciationFeedback ─ @ManyToOne                 ─► Script           (nullable)
PronunciationFeedback ─ @ManyToOne                 ─► Session          (nullable)
```

CHECK 제약으로 강제되는 동시-NOT NULL 규칙은 §2.7 / §2.8 참고.

---

## 4. 인덱스 / 제약 요약

본 절의 표는 **읽는 사람이 읽기 경로를 파악하도록 돕는 가독성 목적의 요약**이며, 그대로 코드 어노테이션이 되는 것은 아니다. MySQL(InnoDB) 은 모든 FK 컬럼에 대해 보조 인덱스를 **자동으로 생성**하므로, 단일 FK 컬럼만으로 구성된 인덱스는 엔티티 코드에서 별도의 `@Index` / `@Table(indexes=...)` 로 선언하지 않는다 (선언해도 중복 인덱스가 생성될 뿐이다). 코드에 `@Index` 로 직접 선언하는 대상은 다중 컬럼 인덱스, FK 가 아닌 컬럼에 대한 인덱스, 그리고 유니크 제약뿐이다.

| 테이블 | 종류 | 이름 | 컬럼 | 코드에 `@Index` / `@UniqueConstraint` 선언? |
| --- | --- | --- | --- | --- |
| `users` | UNIQUE | `uk_users_username` | username | 예 (`@UniqueConstraint`) |
| `users` | UNIQUE | `uk_users_email` | email | 예 (`@UniqueConstraint`) |
| `scripts` | INDEX | `ix_scripts_track` | track_id, chapter_order | 예 (다중 컬럼) |
| `sessions` | INDEX | (FK 자동) | user_id | 아니오 — MySQL 자동 |
| `session_sentences` | INDEX | `ix_session_sentences_session` | session_id, sentence_index | 예 (다중 컬럼) |
| `recordings` | INDEX | (FK 자동) | user_id | 아니오 — MySQL 자동 |
| `recordings` | INDEX | (FK 자동) | script_id | 아니오 — MySQL 자동 |
| `recordings` | INDEX | (FK 자동) | session_id | 아니오 — MySQL 자동 |
| `recordings` | INDEX | (FK 자동) | step_id | 아니오 — MySQL 자동 |
| `recordings` | INDEX | (FK 자동) | session_sentence_id | 아니오 — MySQL 자동 |
| `pronunciation_feedbacks` | INDEX | (FK 자동) | user_id | 아니오 — MySQL 자동 |
| `pronunciation_feedbacks` | INDEX | (FK 자동) | script_id | 아니오 — MySQL 자동 |
| `pronunciation_feedbacks` | INDEX | (FK 자동) | session_id | 아니오 — MySQL 자동 |
| `phoneme_errors` | INDEX | (FK 자동) | feedback_id | 아니오 — MySQL 자동 |

`tracks`, `learning_steps`, `demo_ranking_entries` 는 PK 외 추가 인덱스를 두지 않는다.

---

## 5. 리포지토리 / 서비스 사양 (엔티티 일관성 강제)

엔티티 명세를 그대로 구현하기 위해 엔티티 외부에 두어야 하는 규칙을 한 절에 모은다. 이 규칙들은 race / orphan / cross-tenant 가 데이터 레벨에서 막히도록 보장한다.

### 5.1 사용자 소유 조회 (cross-tenant 방지)

- 사용자 소유 엔티티 (`Session`, `SessionSentence`, `Recording`, `PronunciationFeedback`) 의 리포지토리는 user 스코프가 명시된 메서드만 외부로 노출한다.
  - 예: `findByIdAndUser_Id(Long id, Long userId)`, `findByUser_IdAndIdIn(Long userId, Collection<Long> ids)`, `findByUser_IdOrderByCreatedAtDesc(Long userId)`.
- 글로벌 `findById` 등 user 스코프가 빠진 메서드는 사용자 소유 엔티티에 대해 외부로 노출하지 않는다 (admin/배치 경로는 별도 리포지토리를 둔다).
- 컨트롤러는 `@CurrentUser` 로 받은 `userId` 를 그대로 위 메서드에 넘긴다.

### 5.2 `FeedbackRepository.markCompletedAtomically`

```java
@Modifying
@Query("""
    UPDATE PronunciationFeedback f
       SET f.completed = true
     WHERE f.id = :id
       AND f.user.id = :userId
       AND f.completed = false
""")
int markCompletedAtomically(@Param("id") Long id, @Param("userId") Long userId);
```

- 반환은 영향을 받은 행의 수.
- 보상 가산은 반환값이 1 일 때에만 수행한다. 0 이면 이미 완료 처리된 피드백이거나 cross-user 호출이므로 보상 가산을 건너뛴다.

### 5.3 트랜잭션 경계

- 모든 쓰기 서비스 메서드는 `@Transactional`. 읽기 서비스 메서드는 `@Transactional(readOnly = true)` 를 권장한다.
- `FeedbackService.complete(Long userId, Long feedbackId)` 의 흐름:
  1. `feedbackRepository.markCompletedAtomically(feedbackId, userId)` 호출.
  2. 반환값이 1 → `MemberService.awardCompletionRewards(userId, completionExp)` 로 EXP/streak 가산.
  3. 반환값이 0 → 가산 없이 현재 사용자 정보만 반환.

### 5.4 검증 테스트

엔티티 명세를 그대로 구현했음을 입증하기 위해 다음 테스트를 추가한다.

- **Cross-user 차단**: 사용자 A 의 토큰으로 사용자 B 의 `feedbackId` / `recordingId` / `sessionId` 를 호출하면 404 응답이 나오고, 데이터가 갱신되지 않는다 (Recording, Feedback, Session 각각).
- **Session 대본 갱신 후 녹음 보존**: `Session.updateScript` 가 SessionSentence 행을 새로 교체한 직후, 기존 `Recording.session_sentence_id` 는 NULL 로 끊어지지만 `target_text_snapshot` 은 그대로 남아 어떤 문장에 대한 녹음이었는지 추적 가능하다.
- **완료 동시성**: 동일한 `feedbackId` 에 대해 `complete` 를 두 스레드가 동시에 호출해도 EXP 가 정확히 한 번만 가산된다 (CountDownLatch 기반 동시성 테스트).
- **Recording 정합성 CHECK 제약**: 잘못된 조합(예: script 와 session 동시 NOT NULL, step 만 NOT NULL 이고 script 가 NULL) 으로 INSERT 시 DB 에서 거절된다.

---

## 부록 A. 도메인 enum

- `Difficulty` (`script` 패키지) — `EASY`, `MEDIUM`, `HARD`. `Script.difficulty` 에 `EnumType.STRING` 으로 매핑한다.
- `StepKind` (`learning` 패키지) — `INTRO`(안내, 녹음 없음), `RECORD`(사용자가 발음/녹음 업로드 필요). `LearningStep.kind` 에 `EnumType.STRING` 으로 매핑한다.

## 부록 B. 통계 정책의 위치

- `User.streak` 의 7 캡, 배지 임계, 주간 약점 음소 Top-N 등 통계/보상 정책은 엔티티가 아닌 서비스 계층 (`StatsService`, `BadgePolicy`) 영역이다. 본 명세는 저장 정의에만 한정한다.

## 부록 C. 모델 응답 캐시 컬럼

- `Recording.errors_json` 등은 모델 응답(`ModelAnalyzeResponse`)의 JSON 직렬화 캐시다. 정규화된 별도 테이블을 두지 않는 것은 의도적 설계이며, 분석 결과는 모델 서버가 단일 소스가 된다.

## 부록 D. 시연용 시드

- `DemoRankingEntry` 는 시연 단계의 랭킹 화면을 채우기 위한 보조 테이블이다. 행을 비우면 그대로 사라지고, 데이터는 코드가 아닌 DB 시드로 관리한다.
