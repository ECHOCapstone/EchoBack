package com.capstoneecho.echo_back.learning.script.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendedScriptSelectorTest {

    private final RecommendedScriptSelector selector = new RecommendedScriptSelector();

    @Test
    @DisplayName("same (userId, kstDate, scripts) → same selection result")
    void sameSeedSameResult() throws Exception {
        List<Script> pool = buildPool(10);
        LocalDate day = LocalDate.of(2026, 5, 12);

        List<Script> first = selector.select(42L, day, pool, 3);
        List<Script> second = selector.select(42L, day, pool, 3);

        assertThat(first).hasSize(3);
        assertThat(extractIds(first)).isEqualTo(extractIds(second));
    }

    @Test
    @DisplayName("different KST date with same userId → different selection result")
    void differentDateDifferentResult() throws Exception {
        List<Script> pool = buildPool(10);

        List<Script> today = selector.select(42L, LocalDate.of(2026, 5, 12), pool, 5);
        List<Script> tomorrow = selector.select(42L, LocalDate.of(2026, 5, 13), pool, 5);

        assertThat(extractIds(today)).isNotEqualTo(extractIds(tomorrow));
    }

    @Test
    @DisplayName("count >= pool size → all elements returned (no duplicates)")
    void countLargerThanPoolReturnsAll() throws Exception {
        List<Script> pool = buildPool(4);

        List<Script> picks = selector.select(7L, LocalDate.of(2026, 1, 1), pool, 99);

        assertThat(extractIds(picks))
                .hasSize(4)
                .doesNotHaveDuplicates();
    }

    private static List<Script> buildPool(int size) throws Exception {
        List<Script> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Script script = Script.createStandalone("title-" + i, "content-" + i, Difficulty.EASY);
            setId(script, (long) (i + 1));
            result.add(script);
        }
        return result;
    }

    private static void setId(Script script, Long id) throws Exception {
        Field field = Script.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(script, id);
    }

    private static List<Long> extractIds(List<Script> scripts) {
        return scripts.stream().map(Script::getId).toList();
    }
}
