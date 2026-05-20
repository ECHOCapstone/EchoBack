package com.capstoneecho.echo_back.external.llm;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptCatalogTest {

    private final PromptCatalog catalog = new PromptCatalog();

    @Test
    @DisplayName("system 프롬프트가 로드되고 아학편 가이드 표가 포함되어 있다")
    void systemPromptLoadsAhakpyeon() {
        String body = catalog.raw("system");
        assertThat(body).isNotBlank().contains("아학편").contains("ARPAbet");
    }

    @Test
    @DisplayName("step-feedback / retry-feedback / comprehensive-feedback 템플릿이 존재한다")
    void allUserTemplatesLoad() {
        assertThat(catalog.raw("step-feedback")).contains("{{targetText}}");
        assertThat(catalog.raw("retry-feedback")).contains("{{word}}");
        assertThat(catalog.raw("comprehensive-feedback")).contains("{{chapterTitle}}");
    }

    @Test
    @DisplayName("render 는 변수 치환 + 미사용 placeholder 비워두기")
    void renderSubstitutesAndStripsLeftovers() {
        String body = catalog.render(
                "step-feedback",
                Map.of("targetText", "Hello", "weakPhoneme", "R"));
        assertThat(body).contains("Hello").contains("R").doesNotContain("{{");
    }

    @Test
    @DisplayName("정의되지 않은 키는 예외")
    void unknownKeyThrows() {
        assertThatThrownBy(() -> catalog.raw("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
