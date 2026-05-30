# 재시도 피드백 요청

학습자가 한 단어 (또는 짧은 구) 를 다시 따라 읽었다. 결과를 평가하고 다음 시도용 피드백을 JSON 스키마에 맞춰 돌려준다.

## 연습 항목
{{word}}

## 이번 시도 분석
- 인식된 음소(perceived): {{perceived}}
- 정답 음소(canonical): {{canonical}}
- 음소 오류:
{{errors}}
- 백엔드 점수 (참고용): {{currentScore}}
- 약점 음소: {{weakPhoneme}}

## 같은 항목에 대한 이전 재시도 (오래된 순)
{{priorAttempts}}

## 출력 규칙

- `correct` 는 핵심 약점 음소가 모두 교정되었고 점수가 충분히 높을 때만 `true`.
- `retryRecommended` 는 `correct=false` 이거나 같은 음소를 반복적으로 틀린 경우 `true`.
- `phonemeTips` 는 한국식 발음 가이드 (아학편) 를 따른다. 1~3 개, 비어 있어도 된다.
- 이전 재시도가 누적될수록 `guidanceKr` 은 더 구체적이고 핵심을 짚어 짧게 작성한다.
- `pronunciationGuide` 는 연습 항목 ({{word}}) 에 대해 4파트로 작성한다:
  - `correctPronunciation`: "이렇게 발음해요" — 아학편 한글 음차 기반 정확한 발음 표기 (예: `bus /(으)버스/`)
  - `perceivedPronunciation`: "이렇게 말하신 것 같아요" — 학습자의 실제 발음 추정 표기 (예: `/퍼스/`)
  - `explanation`: 해당 음소가 한국어와 어떻게 다른지 1~2 문장 설명. 아학편 표의 입 모양 · 혀 위치 단서를 활용한다.
  - `correctionTip`: "X 대신 Y 로 발음해 보세요" 형식의 교정 제안 (예: `/퍼스/ 대신 /(으)버스/처럼 발음해 보세요.`)
