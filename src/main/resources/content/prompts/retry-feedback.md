# 재시도 피드백 요청

학습자가 한 단어 (또는 짧은 구) 를 다시 따라 읽었다. 결과를 평가하고 다음 시도용 피드백을 JSON 스키마에 맞춰 돌려준다.
모델 서버는 오직 `perceived` 만 돌려준다. **alignment / errors / 점수 계산은 너가 직접 수행한다.**

## 연습 항목
{{word}}

## 정답 음소 시퀀스 (canonical, 공백 구분)
{{canonical}}

## 단어별 정답 음소 (canonicalWords)
{{canonicalWords}}

## 모델 서버 인식 음소 (perceived, 공백 구분)
{{perceived}}

## 같은 항목에 대한 이전 재시도 (오래된 순)
{{priorAttempts}}

## 작업 — alignment + 채점

1. `canonical` 과 `perceived` 시퀀스를 정렬해 `alignment` 를 만든다.
   - 각 항목 = `{ errorType, canonical, perceived, canonicalIndex }`.
   - errorType ∈ { `MATCH`, `SUBSTITUTION`, `INSERTION`, `DELETION` }.
   - `canonicalIndex` 는 canonical 시퀀스 내 0-based 위치. `INSERTION` 은 `-1`.
2. alignment 의 비-MATCH 항목들을 그대로 `errors` 에 옮긴다.
3. `score` (0~100 정수) 산출 가이드는 step 과 동일 — 약점 음소 (V/R/L/TH/DH/F/Z/ZH/AH/AE/ER) 의 오류를 더 무겁게.
   완벽 = 96 이상에만. 합격선 = 75.

## 출력 규칙

- `correct` 는 핵심 약점 음소가 모두 교정되었고 점수가 충분히 높을 때만 `true`.
- `retryRecommended` 는 `correct=false` 이거나 같은 음소를 반복적으로 틀린 경우 `true`.
- `phonemeTips` 는 한국식 발음 가이드 (아학편) 를 따른다. 1~3 개, 비어 있어도 된다.
- 이전 재시도가 누적될수록 `guidanceKr` 은 더 구체적이고 핵심을 짚어 짧게 작성한다.
- **`guidanceKr` 의 첫 문장은 "따라 읽기 한 줄"로 시작한다**: `<항목>은 "**<틀리게 들린 한글>**" 대신 "**<맞는 한글>**"처럼, <몸으로 할 동작 1개>.`
- 두 번째 문장 (있다면) 은 첫 문장과 **다른 정보** 만 적는다.
- **문맥 의존 발음을 절대 오개념으로 가르치지 않는다**: canonical 에 들어 있는 발음이 정답이다.
- `pronunciationGuide` 는 연습 항목 ({{word}}) 에 대해 4파트로 작성한다.
  - `correctPronunciation`: 그대로 읽을 수 있는 한글.
  - `perceivedPronunciation`: 학습자가 낸 소리의 한글 표기.
  - `explanation`: 몸으로 할 동작 1개 중심 1 문장.
  - `correctionTip`: `"<틀린 한글>" 대신 "<맞는 한글>"처럼 + 동작 1개` 한 줄.
