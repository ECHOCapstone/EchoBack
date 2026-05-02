package com.capstoneecho.echo_back.app.seed;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.LearningStepRepository;
import com.capstoneecho.echo_back.app.script.ScriptRepository;
import com.capstoneecho.echo_back.app.script.Difficulty;
import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 빈 H2 DB 에 시연용 학습 unit 과 단계를 채워 넣는 단발성 초기화기.
// 추천 학습은 잰말놀이 1개 + R/L · V/B 발음 연습 2개로 구성된다.
// 동일 시드의 중복 삽입을 막기 위해 Script 가 이미 있으면 건너뛴다.
@Component
class InitialDataLoader implements ApplicationRunner {

    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;

    InitialDataLoader(ScriptRepository scriptRepository, LearningStepRepository stepRepository) {
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!scriptRepository.findByIsPresetTrueOrderByIdAsc().isEmpty()) {
            return;
        }
        seedTongueTwister();
        seedPronunciationPairRL();
        seedPronunciationPairVB();
    }

    private void seedTongueTwister() {
        var script = scriptRepository.save(Script.create(
                "영어 잰말놀이",
                "I slit the sheet, the sheet I slit, and on the slitted sheet I sit.",
                Difficulty.MEDIUM,
                true
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.record(
                script,
                0,
                "오늘의 잰말놀이에요. 아래 문장을 빠르게 따라 읽어보세요.",
                "I slit the sheet, the sheet I slit, and on the slitted sheet I sit.",
                "ay s l ih t dh ah sh iy t dh ah sh iy t ay s l ih t ae n d aa n dh ah s l ih t ah d sh iy t ay s ih t"
        ));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairRL() {
        var script = scriptRepository.save(Script.create(
                "발음 연습: R vs L",
                "헷갈리는 R 과 L 발음을 단계적으로 구별해 봅니다.",
                Difficulty.MEDIUM,
                true
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(script, 0, "R과 L을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 1, "녹음 버튼을 누르고 R을 발음해 보세요.", "R", "r"));
        steps.add(LearningStep.record(script, 2, "녹음 버튼을 누르고 L을 발음해 보세요.", "L", "l"));
        steps.add(LearningStep.intro(script, 3, "Right와 Light를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 4, "녹음 버튼을 누르고 Right를 발음해 보세요.", "Right", "r ay t"));
        steps.add(LearningStep.record(script, 5, "녹음 버튼을 누르고 Light를 발음해 보세요.", "Light", "l ay t"));
        steps.add(LearningStep.intro(script, 6, "Store와 Stole을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 7, "녹음 버튼을 누르고 Store를 발음해 보세요.", "Store", "s t ao r"));
        steps.add(LearningStep.record(script, 8, "녹음 버튼을 누르고 Stole을 발음해 보세요.", "Stole", "s t ow l"));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairVB() {
        var script = scriptRepository.save(Script.create(
                "발음 연습: V vs B",
                "헷갈리는 V 와 B 발음을 단계적으로 구별해 봅니다.",
                Difficulty.MEDIUM,
                true
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(script, 0, "V와 B를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 1, "녹음 버튼을 누르고 V를 발음해 보세요.", "V", "v"));
        steps.add(LearningStep.record(script, 2, "녹음 버튼을 누르고 B를 발음해 보세요.", "B", "b"));
        steps.add(LearningStep.intro(script, 3, "Vest와 Best를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 4, "녹음 버튼을 누르고 Vest를 발음해 보세요.", "Vest", "v eh s t"));
        steps.add(LearningStep.record(script, 5, "녹음 버튼을 누르고 Best를 발음해 보세요.", "Best", "b eh s t"));
        steps.add(LearningStep.intro(script, 6, "Vine와 Bine을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(script, 7, "녹음 버튼을 누르고 Vine을 발음해 보세요.", "Vine", "v ay n"));
        steps.add(LearningStep.record(script, 8, "녹음 버튼을 누르고 Bine을 발음해 보세요.", "Bine", "b ay n"));
        stepRepository.saveAll(steps);
    }
}
