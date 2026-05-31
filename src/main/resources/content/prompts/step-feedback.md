# Step 피드백 요청

다음 한 번의 녹음 분석을 평가하고 학습자에게 줄 피드백을 JSON 스키마에 맞춰 작성하라.

## 챕터
{{chapterTitle}}

## 목표 문장
{{targetText}}

## 모델 분석 결과
- 인식된 음소(perceived): {{perceived}}
- 정답 음소(canonical): {{canonical}}
- 음소 오류 (op / canonical / perceived / canonicalIndex):
{{errors}}
- 가장 빈번한 약점 음소: {{weakPhoneme}}
- 백엔드 점수 (참고용): {{currentScore}}

## 이 학습자의 같은 step 이전 시도들 (오래된 순)
{{priorAttempts}}

## 출력 규칙

- `wrongWords[i].index` 는 목표 문장을 공백 split 한 단어 배열의 0-based 인덱스다.
- `phonemeTips` 는 약점 음소가 있을 때만 1~3 개. 한국식 발음 표기 가이드 (아학편) 를 따르되, 같은 설명을 여러 tip 에 반복하지 않는다.
- `strengths` / `weaknesses` 는 각각 1~3 개. 비어 있어도 된다.
- 이전 시도가 있고 같은 음소를 반복해서 틀렸다면 `guidanceKr` 에 그 점을 반드시 언급하라.
- **`guidanceKr` 의 첫 문장은 가장 약한 단어의 "따라 읽기 한 줄"로 시작한다**: `<단어>는 "<틀리게 들린 한글>" 대신 "<맞는 한글>"처럼, <몸으로 할 동작 1개>.` (예: `results 는 "리절츠" 대신 "리저얼즈"처럼, 끝을 목 울리며 'ㅈ'까지.`) 그 뒤에 짧은 격려/맥락을 붙인다.
- `pronunciationGuide` 는 **가장 두드러진 약점 단어 1개**(문장이어도 1개로 좁힌다) 에 대해 4파트로 작성한다. 모든 표기는 **그대로 입으로 읽을 수 있는 한글**로 적는다 (ARPAbet/IPA/읽을 수 없는 기호 금지):
  - `correctPronunciation`: "이렇게 발음해요" — 읽을 수 있는 한글 (예: `(으)롸이스`, `리저얼즈`)
  - `perceivedPronunciation`: "이렇게 말하신 것 같아요" — 학습자가 낸 소리를 한글로 (예: `(을)라이스`, `리절츠`)
  - `explanation`: 한국어와 무엇이 다른지 **몸으로 할 동작 1개** 중심으로 1 문장. 추상 설명 금지.
  - `correctionTip`: `"<틀린 한글>" 대신 "<맞는 한글>"처럼 + 동작 1개` 한 줄 (예: `"리절츠" 대신 "리저얼즈"처럼, 끝을 목 울리며 'ㅈ'까지 발음해 보세요.`)
