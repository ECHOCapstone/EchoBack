package com.capstoneecho.echo_back.learning.script.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LearningStepFactoryTest {

    private Script chapterScript() {
        Track track = Track.create("Track1", "desc", 1);
        return Script.createChapter(track, 1, "Chapter1", "hello world", Difficulty.EASY, null, null);
    }

    @Test
    @DisplayName("intro 는 targetText 를 보유하지 않으며 kind=INTRO")
    void introHasNoTargetText() {
        Script script = chapterScript();

        LearningStep step = LearningStep.intro(script, "안내 문구");

        assertThat(step.getKind()).isEqualTo(StepKind.INTRO);
        assertThat(step.getTargetText()).isNull();
        assertThat(step.getPrompt()).isEqualTo("안내 문구");
        assertThat(step.getScript()).isSameAs(script);
    }

    @Test
    @DisplayName("intro 는 prompt 가 blank 면 거부")
    void introRejectsBlankPrompt() {
        Script script = chapterScript();

        assertThatThrownBy(() -> LearningStep.intro(script, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("record 는 prompt 와 targetText 를 모두 요구한다")
    void recordRequiresTargetText() {
        Script script = chapterScript();

        assertThatThrownBy(() -> LearningStep.record(script, "다음을 발음하세요", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LearningStep.record(script, "다음을 발음하세요", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("record 는 kind=RECORD 와 targetText 를 보유")
    void recordCarriesTargetText() {
        Script script = chapterScript();

        LearningStep step = LearningStep.record(script, "다음을 발음하세요", "Hello world");

        assertThat(step.getKind()).isEqualTo(StepKind.RECORD);
        assertThat(step.getTargetText()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("intro/record 는 script null 을 거부")
    void factoriesRejectNullScript() {
        assertThatThrownBy(() -> LearningStep.intro(null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LearningStep.record(null, "x", "y"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
