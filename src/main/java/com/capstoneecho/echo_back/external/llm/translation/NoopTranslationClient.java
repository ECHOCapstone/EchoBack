package com.capstoneecho.echo_back.external.llm.translation;

import java.util.List;
import org.springframework.stereotype.Component;

// 활성 provider 가 번역을 제공하지 못할 때 사용되는 폴백. 입력 개수에 맞춰 빈 문자열을 돌려준다.
// 호출 측 (TranslationService) 은 빈 결과를 캐시에 저장하지 않고 FE 는 빈 번역을 만나면 표시를 생략한다.
@Component
public class NoopTranslationClient implements TranslationClient {

    @Override
    public List<String> translate(List<String> sourceTexts) {
        if (sourceTexts == null || sourceTexts.isEmpty()) {
            return List.of();
        }
        return sourceTexts.stream().map(text -> "").toList();
    }
}
