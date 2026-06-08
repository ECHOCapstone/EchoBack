# 한 번의 녹음 평가

## 입력

- 챕터: {{chapterTitle}}
- 문장: {{targetText}}
- canonical (단어별):
{{canonicalWords}}
- perceived: {{perceived}}
- 이전 시도:
{{priorAttempts}}

## 작업

### alignment + errors

canonical 음소 시퀀스와 perceived 를 정렬해 각 항목을 `{ errorType, canonical, perceived, canonicalIndex }` 로 표현한다. errorType ∈ { MATCH, SUBSTITUTION, INSERTION, DELETION }. INSERTION 의 canonicalIndex 는 -1. 자연스러운 미국식 연결 발음 (liaison, flapping T, weak form) 으로 보이는 항목은 MATCH 로 인정한다.

비-MATCH 항목을 errors 로 옮긴다.

### score (0~100 정수)

- alignment 가 전부 MATCH 이고 errors 가 비면 96~100.
- 그 외에는 베이스 `(MATCH 수 / canonical 길이) × 100` 에서 다음을 차감:
  - 약점 음소 (V R L TH DH F Z ZH AH AE ER) 의 SUBSTITUTION/DELETION: 각 -5
  - INSERTION: 각 -3
- 0~100 정수로 정리. 합격선 75.

### 한국어 피드백

guidanceKr 의 첫 문장은 가장 약한 단어의 따라 읽기 한 줄:
`<단어>는 "**<틀린 한글>**" 대신 "**<맞는 한글>**"처럼, <몸으로 할 동작 1개>.`

phonemeTips 는 errors 안의 음소 1~3 개. 한국식 발음 표기 가이드 (시스템 프롬프트의 아학편 표) 를 따른다. 슈와 (AH) 보다 다른 음소가 있으면 그쪽을 우선.

wrongWords[i].index 는 targetText 를 공백 split 한 단어 배열의 0-based 인덱스다. 단어 매핑은 canonical (단어별) 에서 canonicalIndex 가 속한 단어를 찾아 부여한다.

pronunciationGuide 는 가장 두드러진 약점 단어 1개:
- correctPronunciation: 그대로 읽을 수 있는 한글
- perceivedPronunciation: 학습자가 낸 소리의 한글
- explanation: 몸으로 할 동작 1개 중심 1 문장
- correctionTip: `"<틀린 한글>" 대신 "<맞는 한글>"처럼 + 동작 1개`

retryRecommended = score < 75 이거나 같은 약점 음소가 여러 번 반복 등장하면 true.
