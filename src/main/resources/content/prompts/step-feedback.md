# Step 피드백 요청

다음 한 번의 녹음 분석을 평가하고 학습자에게 줄 피드백을 JSON 스키마에 맞춰 작성하라.
모델 서버는 오직 `perceived` (인식 음소 시퀀스) 만 돌려준다. **alignment / errors / 점수 계산은 너가 직접 수행한다.**

## 챕터
{{chapterTitle}}

## 목표 문장
{{targetText}}

## 정답 음소 시퀀스 (canonical, 공백 구분)
{{canonical}}

## 단어별 정답 음소 (canonicalWords)
{{canonicalWords}}

## 모델 서버 인식 음소 (perceived, 공백 구분)
{{perceived}}

## 이 학습자의 같은 step 이전 시도들 (오래된 순)
{{priorAttempts}}

## 작업 — alignment + 채점

1. `canonical` 과 `perceived` 시퀀스를 정렬해 `alignment` 를 만든다.
   - 각 항목 = `{ errorType, canonical, perceived, canonicalIndex }`.
   - errorType ∈ { `MATCH`, `SUBSTITUTION`, `INSERTION`, `DELETION` }.
   - `canonicalIndex` 는 canonical 시퀀스 내 0-based 위치. `INSERTION` 은 정답에 없는 음소이므로 `-1`.
2. alignment 의 비-MATCH 항목들을 그대로 `errors` 에 옮긴다 (`op` 는 errorType 과 동일한 enum).
3. `score` (0~100 정수) 산출 가이드:
   - 정확도 베이스 = `(MATCH 수) / (canonical 길이)` × 100.
   - 약점 음소 (V/R/L/TH/DH/F/Z/ZH/AH/AE/ER) 의 오류는 학습 가치 ↑ — 한국인 학습자에게 더 무겁게 잡고 점수를 더 깎는다.
   - INSERTION 은 SUBSTITUTION/DELETION 보다 약간 가볍게 본다.
   - 완벽 = 96 이상에만. 합격선 = 75.
4. `retryRecommended` = `score < 75` 또는 같은 약점 음소가 여러 번 반복 등장하면 `true`.

## 출력 규칙

- `wrongWords[i].index` 는 목표 문장을 공백 split 한 단어 배열의 0-based 인덱스다.
  단어 매핑은 `canonicalWords` 에서 canonicalIndex 가 속한 단어를 찾아 부여한다.
- `phonemeTips` 는 약점 음소가 있을 때만 1~3 개. 한국식 발음 표기 가이드 (아학편) 를 따른다.
- `phonemeTips[].phoneme` 은 `errors` 안에서 가장 학습 가치가 큰 음소를 고른다.
  **슈와 (AH) 는 광범위하게 등장해 단어 단위 가이드가 모호하므로, errors 에 다른 음소가 있으면 그쪽을 우선**한다.
- 이전 시도가 있고 같은 음소를 반복해서 틀렸다면 `guidanceKr` 에 그 점을 반드시 언급하라.
- **`guidanceKr` 의 첫 문장은 가장 약한 단어의 "따라 읽기 한 줄"로 시작한다**: `<단어>는 "**<틀리게 들린 한글>**" 대신 "**<맞는 한글>**"처럼, <몸으로 할 동작 1개>.` (예: `results 는 "**리절츠**" 대신 "**리저얼즈**"처럼, 끝을 목 울리며 'ㅈ'까지.`)
- 두 번째 문장 (있다면) 은 첫 문장과 **다른 정보** 만 적는다. 같은 비교를 반복하지 않는다.
- **문맥 의존 발음을 절대 오개념으로 가르치지 않는다**: "the event" 에서 `the` 는 `DH IY` (디) 가 정답이다. canonical 에 그렇게 들어 있으면 그대로 정답으로 인정하고, 학습자가 "더" 로 발음했다면 그 점을 짚어 교정한다.
- `pronunciationGuide` 는 **가장 두드러진 약점 단어 1개** 에 대해 4파트로 작성한다.
  - `correctPronunciation`: "이렇게 발음해요" — 그대로 읽을 수 있는 한글 (예: `리저얼즈`).
  - `perceivedPronunciation`: "이렇게 말하신 것 같아요" — 학습자가 낸 소리의 한글 표기.
  - `explanation`: 한국어와 무엇이 다른지 **몸으로 할 동작 1개** 중심으로 1 문장.
  - `correctionTip`: `"<틀린 한글>" 대신 "<맞는 한글>"처럼 + 동작 1개` 한 줄.
