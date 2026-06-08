# Canonical 보정

CMU 사전 기반의 단어 단위 baseline canonical 이 함께 주어진다. 이를 입력으로 받아 자연스러운 General American 발음에 맞도록 보정한다.

## 입력

텍스트: {{text}}

baseline (CMU 단어 단위):
{{baseline}}

## 인벤토리

{{inventory}}

## 작업

1. baseline 의 각 단어 음소를 검토하고 문맥에 따라 보정한다 — 모음 앞 the (DH AH → DH IY), 강조 a (AH → EY), 약화된 function word 등.
2. baseline 에 빠진 단어가 있으면 인벤토리 안의 음소로 채운다.
3. 출력은 baseline 과 같은 단어 순서를 유지하고, 강세 숫자 없이 베이스 코드만 사용한다.

## 출력

{ "words": [{ "word": "the", "phonemes": ["DH", "IY"] }, ...] }
