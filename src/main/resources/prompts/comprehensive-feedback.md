# 종합 피드백 요청

한 챕터의 모든 step 학습이 끝났다. 챕터 전반의 흐름을 보고 종합 피드백 + 다음 학습 추천을 JSON 스키마에 맞춰 돌려준다.

## 챕터
- 제목: {{chapterTitle}}
- 내용: {{chapterContent}}

## 챕터 전체 통계
- 평균 정확도: {{overallAccuracy}}
- 가장 두드러진 약점 음소: {{dominantWeakPhoneme}}

## Step 별 최종 결과 (순서대로)
{{stepSummaries}}

## 챕터 누적 음소 오류 (op / canonical / perceived)
{{aggregatedErrors}}

## 출력 규칙

- `overallScore` 는 step 들의 best 점수 평균에 약점 음소 보정을 가감해 0~100 정수로 산출.
- `summaryKr` 은 2~3 문장. 챕터의 전체 인상 + 가장 큰 강점/약점 한 가지씩.
- `strengths` / `weaknesses` 는 각각 2~4 개. 학습자가 다음 챕터로 들고 갈 만한 것들로.
- `nextPracticeItems` 는 3~5 개. **단어 / 구 / 문장이 적절히 섞이도록** 추천하라.
  - WORD: 약점 음소가 두드러지는 단음절~다음절 영어 단어
  - PHRASE: 자주 같이 쓰이는 짧은 구 (2~4 단어)
  - SENTENCE: 약점 음소가 여러 번 등장하는 자연스러운 영어 문장
  - 각 항목의 `reason` 은 학습자에게 노출되는 추천 사유 한 줄.
