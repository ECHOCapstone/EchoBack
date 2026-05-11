package com.capstoneecho.echo_back.learning.script.support;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class RecommendedScriptSelector {

    public List<Script> select(Long userId, LocalDate kstDate, List<Script> scripts, int count) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(kstDate, "kstDate");
        Objects.requireNonNull(scripts, "scripts");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        if (scripts.isEmpty() || count == 0) {
            return List.of();
        }
        List<Script> shuffled = new ArrayList<>(scripts);
        long seed = Objects.hash(userId, kstDate.toString());
        Collections.shuffle(shuffled, new Random(seed));
        int take = Math.min(count, shuffled.size());
        return List.copyOf(shuffled.subList(0, take));
    }
}
