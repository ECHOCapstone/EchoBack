# Canonical 생성 요청

영어 문장을 받아 단어별 ARPABET 음소 시퀀스 (canonical) 를 만든다. 다른 일을 하지 않는다.

## 입력
{{text}}

## 인벤토리 — 출력에 이 41개 ARPABET 음소 외 토큰은 절대 등장하지 않는다

{{inventory}}

## 작업 규칙

1. 입력 문장을 단어 경계 (공백, 하이픈, 어퍼스트로피로 묶인 contraction) 로 끊는다.
   - "don't" → 한 단어. "co-worker" → 한 단어. "the event" → 두 단어.
   - 출력의 `words[i].word` 는 입력 원문에서 추출한 표면형 그대로 (대소문자 / 어퍼스트로피 / 하이픈 보존).
2. 각 단어에 대해 그 단어 한 개의 ARPABET 음소 시퀀스를 부여한다. **강세 표기 (AH0 / AH1 / AH2 등의 숫자) 는 절대 붙이지 않는다** — 위 인벤토리의 베이스 코드만 쓴다.
3. **문맥 의존 발음 규칙** — 같은 철자라도 문맥에 따라 다르게 발음되는 단어를 정확히 처리한다.
   - **the**:
     - 다음 단어가 모음 소리로 시작 (a / e / i / o / u, 그 외 모음 발음 단어) → `DH IY` ("디")
     - 그 외 → `DH AH` ("더")
   - **a**: 강조 / 단독 → `EY`, 약한 자리 → `AH`
   - **of / to / for / and / are / can / do / will / has / have**: 약한 자리에 등장하면 reduce form 의 schwa (AH) 를 적용한다. 단독 강조 시에는 full form.
4. **연음 / reduce form** — 학습자가 실제로 듣고 따라 읽을 형태로 적는다 (영어 원어민이 일상 속도로 발음한 형태).
   - "gonna" / "wanna" / "gotta" / "kinda" — 입력에 그대로 들어오면 그대로 사용 (going to 를 임의로 reduce 하지 않는다).
   - "want to" → 두 단어로 끊고 "to" 는 reduce form (`T AH`) 로 둔다.
5. 구두점 (`.`, `,`, `!`, `?`, `;`, `:`, `"`, `'`) 은 단어에서 떼어내고 출력에 포함하지 않는다.
6. 숫자 (`1`, `42` 등) 는 영어 읽음 (`one`, `forty two`) 으로 풀어 적용한다.
7. 인벤토리에 없는 외래어 / 새 음소가 필요하면 가장 가까운 ARPABET 음소로 근사한다. 인벤토리 외 코드를 출력하면 응답이 거부된다.

## 예시

### 예시 1 — 문맥 의존 the
입력: `the event is over`
출력:
```
{
  "words": [
    { "word": "the",   "phonemes": ["DH", "IY"] },
    { "word": "event", "phonemes": ["IH", "V", "EH", "N", "T"] },
    { "word": "is",    "phonemes": ["IH", "Z"] },
    { "word": "over",  "phonemes": ["OW", "V", "ER"] }
  ]
}
```

### 예시 2 — 자음 시작 the
입력: `the book`
출력:
```
{
  "words": [
    { "word": "the",  "phonemes": ["DH", "AH"] },
    { "word": "book", "phonemes": ["B", "UH", "K"] }
  ]
}
```

### 예시 3 — reduce form 과 contraction
입력: `I gonna go for a walk.`
출력:
```
{
  "words": [
    { "word": "I",     "phonemes": ["AY"] },
    { "word": "gonna", "phonemes": ["G", "AH", "N", "AH"] },
    { "word": "go",    "phonemes": ["G", "OW"] },
    { "word": "for",   "phonemes": ["F", "ER"] },
    { "word": "a",     "phonemes": ["AH"] },
    { "word": "walk",  "phonemes": ["W", "AO", "K"] }
  ]
}
```
