package com.capstoneecho.echo_back.app.seed;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.LearningStepRepository;
import com.capstoneecho.echo_back.app.ranking.DemoRankingEntry;
import com.capstoneecho.echo_back.app.ranking.DemoRankingEntryRepository;
import com.capstoneecho.echo_back.app.script.Difficulty;
import com.capstoneecho.echo_back.app.script.Script;
import com.capstoneecho.echo_back.app.script.ScriptRepository;
import com.capstoneecho.echo_back.app.track.Track;
import com.capstoneecho.echo_back.app.track.TrackRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 빈 H2 DB 에 시연용 트랙·챕터·스텝과 데모 랭킹 엔트리를 채워 넣는 단발성 초기화기.
// 트랙 1개("기본 발음 트랙") 안에 잰말놀이 + R/L + V/B + F/P + TH 다섯 챕터를 순서대로 배치한다.
// 동일 시드의 중복 삽입을 막기 위해 트랙이 이미 존재하면 트랙 시드는 건너뛴다.
// 데모 랭킹 엔트리도 동일 정책으로 비어 있을 때만 채운다.
@Component
class InitialDataLoader implements ApplicationRunner {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;
    private final DemoRankingEntryRepository demoRankingRepository;

    InitialDataLoader(
            TrackRepository trackRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository stepRepository,
            DemoRankingEntryRepository demoRankingRepository
    ) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
        this.demoRankingRepository = demoRankingRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTracksIfEmpty();
        seedDemoRankingIfEmpty();
    }

    private void seedTracksIfEmpty() {
        if (!trackRepository.findAll().isEmpty()) {
            return;
        }
        var basicTrack = trackRepository.save(Track.create(
                "기본 발음 트랙",
                "잰말놀이부터 R/L · V/B · F/P · TH 까지, 영어 발음의 기본기를 빠르게 잡는 입문 트랙.",
                0
        ));
        seedTongueTwisterChapter(basicTrack);
        seedPronunciationPairRLChapter(basicTrack);
        seedPronunciationPairVBChapter(basicTrack);
        seedPronunciationPairFPChapter(basicTrack);
        seedPronunciationPairTHChapter(basicTrack);
    }

    // 시연 단계의 가짜 사용자 15명. 실제 PronunciationFeedback 누적이 충분해질 때까지의 임시 데이터로,
    // 운영 전환 시 demo_ranking_entries 테이블을 비우면 자동으로 사라진다.
    private void seedDemoRankingIfEmpty() {
        if (demoRankingRepository.count() > 0) {
            return;
        }
        demoRankingRepository.saveAll(List.of(
                DemoRankingEntry.of("jenny01", 98.4),
                DemoRankingEntry.of("minsu_kim", 96.1),
                DemoRankingEntry.of("sarahLee", 94.7),
                DemoRankingEntry.of("davidPark", 92.3),
                DemoRankingEntry.of("happyCat", 90.8),
                DemoRankingEntry.of("tomBrown", 88.5),
                DemoRankingEntry.of("lily2024", 86.2),
                DemoRankingEntry.of("jakePhd", 84.0),
                DemoRankingEntry.of("rosie_h", 81.6),
                DemoRankingEntry.of("mikeWong", 78.9),
                DemoRankingEntry.of("annaJung", 76.4),
                DemoRankingEntry.of("kevin99", 73.1),
                DemoRankingEntry.of("sunnyDay", 69.8),
                DemoRankingEntry.of("leoChoi", 65.5),
                DemoRankingEntry.of("mia_park", 60.2)
        ));
    }

    private void seedTongueTwisterChapter(Track track) {
        // 잰말놀이는 빈도형 챌린지(N회 완료) 로 평가하기 때문에 마스터 배지를 부여하지 않는다.
        var chapter = scriptRepository.save(Script.createChapter(
                track,
                0,
                "영어 잰말놀이",
                "I slit the sheet, the sheet I slit, and on the slitted sheet I sit.",
                Difficulty.MEDIUM,
                "sheet",
                null
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.record(
                chapter,
                0,
                "오늘의 잰말놀이에요. 아래 문장을 빠르게 따라 읽어보세요.",
                "I slit the sheet, the sheet I slit, and on the slitted sheet I sit.",
                "ay s l ih t dh ah sh iy t dh ah sh iy t ay s l ih t ae n d aa n dh ah s l ih t ah d sh iy t ay s ih t"
        ));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairRLChapter(Track track) {
        var chapter = scriptRepository.save(Script.createChapter(
                track,
                1,
                "발음 연습: R vs L",
                "헷갈리는 R 과 L 발음을 단계적으로 구별해 봅니다.",
                Difficulty.MEDIUM,
                "light",
                "R vs L 마스터"
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(chapter, 0, "R과 L을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 1, "녹음 버튼을 누르고 R을 발음해 보세요.", "R", "r"));
        steps.add(LearningStep.record(chapter, 2, "녹음 버튼을 누르고 L을 발음해 보세요.", "L", "l"));
        steps.add(LearningStep.intro(chapter, 3, "Right와 Light를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 4, "녹음 버튼을 누르고 Right를 발음해 보세요.", "Right", "r ay t"));
        steps.add(LearningStep.record(chapter, 5, "녹음 버튼을 누르고 Light를 발음해 보세요.", "Light", "l ay t"));
        steps.add(LearningStep.intro(chapter, 6, "Store와 Stole을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 7, "녹음 버튼을 누르고 Store를 발음해 보세요.", "Store", "s t ao r"));
        steps.add(LearningStep.record(chapter, 8, "녹음 버튼을 누르고 Stole을 발음해 보세요.", "Stole", "s t ow l"));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairVBChapter(Track track) {
        var chapter = scriptRepository.save(Script.createChapter(
                track,
                2,
                "발음 연습: V vs B",
                "헷갈리는 V 와 B 발음을 단계적으로 구별해 봅니다.",
                Difficulty.MEDIUM,
                "vest",
                "V vs B 마스터"
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(chapter, 0, "V와 B를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 1, "녹음 버튼을 누르고 V를 발음해 보세요.", "V", "v"));
        steps.add(LearningStep.record(chapter, 2, "녹음 버튼을 누르고 B를 발음해 보세요.", "B", "b"));
        steps.add(LearningStep.intro(chapter, 3, "Vest와 Best를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 4, "녹음 버튼을 누르고 Vest를 발음해 보세요.", "Vest", "v eh s t"));
        steps.add(LearningStep.record(chapter, 5, "녹음 버튼을 누르고 Best를 발음해 보세요.", "Best", "b eh s t"));
        steps.add(LearningStep.intro(chapter, 6, "Vine와 Bine을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 7, "녹음 버튼을 누르고 Vine을 발음해 보세요.", "Vine", "v ay n"));
        steps.add(LearningStep.record(chapter, 8, "녹음 버튼을 누르고 Bine을 발음해 보세요.", "Bine", "b ay n"));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairFPChapter(Track track) {
        var chapter = scriptRepository.save(Script.createChapter(
                track,
                3,
                "발음 연습: F vs P",
                "헷갈리는 F 와 P 발음을 단계적으로 구별해 봅니다.",
                Difficulty.MEDIUM,
                "fine",
                "F vs P 마스터"
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(chapter, 0, "F와 P를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 1, "녹음 버튼을 누르고 F를 발음해 보세요.", "F", "f"));
        steps.add(LearningStep.record(chapter, 2, "녹음 버튼을 누르고 P를 발음해 보세요.", "P", "p"));
        steps.add(LearningStep.intro(chapter, 3, "Fine과 Pine을 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 4, "녹음 버튼을 누르고 Fine을 발음해 보세요.", "Fine", "f ay n"));
        steps.add(LearningStep.record(chapter, 5, "녹음 버튼을 누르고 Pine을 발음해 보세요.", "Pine", "p ay n"));
        steps.add(LearningStep.intro(chapter, 6, "Coffee와 Copy를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 7, "녹음 버튼을 누르고 Coffee를 발음해 보세요.", "Coffee", "k ao f iy"));
        steps.add(LearningStep.record(chapter, 8, "녹음 버튼을 누르고 Copy를 발음해 보세요.", "Copy", "k aa p iy"));
        stepRepository.saveAll(steps);
    }

    private void seedPronunciationPairTHChapter(Track track) {
        var chapter = scriptRepository.save(Script.createChapter(
                track,
                4,
                "발음 연습: TH vs DH",
                "무성음 TH (think) 와 유성음 DH (this) 의 차이를 단계적으로 익힙니다.",
                Difficulty.HARD,
                "think",
                "TH 마스터"
        ));
        var steps = new ArrayList<LearningStep>();
        steps.add(LearningStep.intro(chapter, 0, "무성 TH 와 유성 DH 를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 1, "녹음 버튼을 누르고 무성 TH (혀끝 윗니 사이) 를 발음해 보세요.", "TH", "th"));
        steps.add(LearningStep.record(chapter, 2, "녹음 버튼을 누르고 유성 DH (성대 울림) 를 발음해 보세요.", "DH", "dh"));
        steps.add(LearningStep.intro(chapter, 3, "Think 와 This 를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 4, "녹음 버튼을 누르고 Think 를 발음해 보세요.", "Think", "th ih ng k"));
        steps.add(LearningStep.record(chapter, 5, "녹음 버튼을 누르고 This 를 발음해 보세요.", "This", "dh ih s"));
        steps.add(LearningStep.intro(chapter, 6, "Three 와 Free 를 각각 발음해 볼 겁니다."));
        steps.add(LearningStep.record(chapter, 7, "녹음 버튼을 누르고 Three 를 발음해 보세요.", "Three", "th r iy"));
        steps.add(LearningStep.record(chapter, 8, "녹음 버튼을 누르고 Free 를 발음해 보세요.", "Free", "f r iy"));
        stepRepository.saveAll(steps);
    }
}
