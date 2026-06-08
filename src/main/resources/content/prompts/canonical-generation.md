# Canonical 생성

영어 텍스트의 자연스러운 General American 발음을 단어별 ARPABET 음소 시퀀스로 표현한다.

## 입력

텍스트: {{text}}

{{perceivedSection}}

## 인벤토리

{{inventory}}

## 작업

1. 단어 경계로 끊는다 (공백 / 하이픈 / 어퍼스트로피 contraction). 구두점은 떼어내고, 숫자는 영어 단어로 푼다.
2. 각 단어의 ARPABET 음소를 부여한다. 강세 숫자 없이 베이스 코드만 사용한다 (인벤토리 표 참고).
3. 자연스러운 일상 속도의 발음을 반영한다 — 문맥에 따른 발음 차이 (the/a/to/of 같은 function word 의 약화, 모음 앞 the 의 DH IY 등), reduce form (gonna, wanna 같이 입력에 그대로 있으면 그대로) 모두 포함한다.
4. perceived 가 함께 주어졌다면 (= 연습 첫 시도), 학습자가 자연스러운 연결 발음 (liaison, weak form, flapping T) 으로 발음한 부분은 canonical 에 그대로 반영해 학습자의 발음 스타일을 정답으로 받아준다. perceived 가 의미를 잃을 정도로 망가졌다면 표준 발음을 사용한다.

## 출력

{ "words": [{ "word": "the", "phonemes": ["DH", "IY"] }, ...] }
