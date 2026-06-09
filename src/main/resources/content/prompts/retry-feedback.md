# 재시도 평가

학습자가 한 단어 (또는 짧은 구) 를 다시 따라 읽었다. canonical 은 미리 결정돼 있으니 alignment 와 피드백만 작성한다.

## 입력

- 항목: {{word}}
- canonical (단어별):
{{canonicalWords}}
- perceived: {{perceived}}
- 이전 재시도:
{{priorAttempts}}
- 참고 정렬 (알고리즘 결정적 정렬 — 점수 산출의 기준):
{{alignment}}

## 작업

### alignment + errors

위 **참고 정렬**을 핵심 근거로 삼아 각 항목을 `{ errorType, canonical, perceived, canonicalIndex }` 로 표현한다. errorType ∈ { MATCH, SUBSTITUTION, INSERTION, DELETION }. INSERTION 의 canonicalIndex 는 -1.

- 참고 정렬의 비-MATCH(특히 약점 음소)는 **빠뜨리지 말고 반영**한다.
- 다만 명백히 잡음이 음소로 오인식된 군더더기(불필요한 INSERTION)는 정리할 수 있고, 자연스러운 미국식 연결 발음(liaison, flapping T, weak form)은 MATCH 로 둘 수 있다.

비-MATCH 항목을 errors 로 옮긴다.

### correct + retryRecommended

- correct = 핵심 약점 음소가 모두 교정되고 errors 가 비거나 사소한 정도일 때 true.
- retryRecommended = correct=false 이거나 같은 음소를 반복적으로 틀린 경우 true.

### 한국어 피드백

guidanceKr 의 첫 문장은 따라 읽기 한 줄:
`<항목>은 "**<틀린 한글>**" 대신 "**<맞는 한글>**"처럼, <몸으로 할 동작 1개>.`

이전 재시도가 누적될수록 핵심을 짚는다.

phonemeTips 는 errors 안의 음소 1~3 개. 한국식 발음 표기 가이드 (시스템 프롬프트의 아학편 표) 를 따른다.

pronunciationGuide 는 항목 ({{word}}) 에 대해:
- correctPronunciation: 그대로 읽을 수 있는 한글
- perceivedPronunciation: 학습자가 낸 소리의 한글
- explanation: 몸으로 할 동작 1개 중심 1 문장
- correctionTip: `"<틀린 한글>" 대신 "<맞는 한글>"처럼 + 동작 1개`
