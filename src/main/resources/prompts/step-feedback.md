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
- `phonemeTips` 는 약점 음소가 있을 때만 1~3 개. 한국식 발음 표기 가이드 (아학편) 를 따른다.
- `strengths` / `weaknesses` 는 각각 1~3 개. 비어 있어도 된다.
- 이전 시도가 있고 같은 음소를 반복해서 틀렸다면 `guidanceKr` 에 그 점을 반드시 언급하라.
