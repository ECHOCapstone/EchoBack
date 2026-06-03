package com.capstoneecho.echo_back.external.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptCatalogTest {

    // 빈 임시 디렉터리를 편집본 위치로 둔다 — 파일 오버라이드 없는 상태,
    // 즉 classpath 기본 본문만으로 검증한다.
    @TempDir
    Path contentDir;

    private PromptCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new PromptCatalog(contentDir.toString(), new PersistableContentStore(contentDir.toString()));
    }

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

    @Test
    @DisplayName("override 는 편집본 파일을 쓰고, 새 카탈로그가 그 파일을 읽어 유지된다")
    void overridePersistsToFileAcrossReload() {
        catalog.override("system", "재정의 본문");
        assertThat(catalog.raw("system")).isEqualTo("재정의 본문");

        // 같은 디렉터리로 다시 만든 카탈로그가 편집본을 읽어온다 (재시작 후 유지).
        PromptCatalog reloaded = new PromptCatalog(contentDir.toString(), new PersistableContentStore(contentDir.toString()));
        assertThat(reloaded.get("system").overridden()).isTrue();
        assertThat(reloaded.raw("system")).isEqualTo("재정의 본문");

        // reset 은 파일을 지워 기본값으로 되돌린다.
        reloaded.reset("system");
        assertThat(reloaded.get("system").overridden()).isFalse();
        assertThat(reloaded.raw("system")).contains("아학편");
    }
}
