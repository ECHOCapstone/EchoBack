# 한 번의 녹음 평가

## 입력

- 챕터: {{chapterTitle}}
- 문장: {{targetText}}
- canonical (단어별):
{{canonicalWords}}
- perceived: {{perceived}}
- 이전 시도:
{{priorAttempts}}
- 참고 정렬 (알고리즘 결정적 정렬 — 점수 산출의 기준):
{{alignment}}

## 작업

### alignment + errors

위 **참고 정렬**을 핵심 근거로 삼아 각 항목을 `{ errorType, canonical, perceived, canonicalIndex }` 로 표현한다. errorType ∈ { MATCH, SUBSTITUTION, INSERTION, DELETION }. INSERTION 의 canonicalIndex 는 -1.

- 참고 정렬의 비-MATCH(특히 약점 음소)는 **빠뜨리지 말고 반영**한다 — 학습자가 본 피드백과 화면 표시가 어긋나지 않게.
- 다만 명백히 주변 잡음이 음소로 오인식된 군더더기(불필요한 INSERTION)는 정리할 수 있고, 자연스러운 미국식 연결 발음(liaison, flapping T, weak form)은 MATCH 로 둘 수 있다.

비-MATCH 항목을 errors 로 옮긴다.

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

retryRecommended 는 errors 가 많거나 같은 약점 음소가 여러 번 반복 등장할 때 true.
