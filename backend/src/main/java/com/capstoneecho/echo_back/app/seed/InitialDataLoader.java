package com.capstoneecho.echo_back.app.seed;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.LearningStepRepository;
import com.capstoneecho.echo_back.app.learning.StepKind;
import com.capstoneecho.echo_back.app.ranking.DemoRankingEntry;
import com.capstoneecho.echo_back.app.ranking.DemoRankingEntryRepository;
import com.capstoneecho.echo_back.app.script.Difficulty;
import com.capstoneecho.echo_back.app.script.Script;
import com.capstoneecho.echo_back.app.script.ScriptRepository;
import com.capstoneecho.echo_back.app.track.Track;
import com.capstoneecho.echo_back.app.track.TrackRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;

// 부팅 시 H2 가 비어 있으면 src/main/resources/seed/*.json 의 트랙/챕터/스텝과 데모 랭킹을
// 한 번 채워 넣는다. 이미 행이 있으면 그대로 둔다.
@Component
class InitialDataLoader implements ApplicationRunner {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;
    private final DemoRankingEntryRepository demoRankingRepository;
    private final ObjectMapper objectMapper;
    private final Resource tracksResource;
    private final Resource demoRankingResource;

    InitialDataLoader(
            TrackRepository trackRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository stepRepository,
            DemoRankingEntryRepository demoRankingRepository,
            ObjectMapper objectMapper,
            @Value("classpath:seed/tracks.json") Resource tracksResource,
            @Value("classpath:seed/demo-ranking.json") Resource demoRankingResource
    ) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
        this.demoRankingRepository = demoRankingRepository;
        this.objectMapper = objectMapper;
        this.tracksResource = tracksResource;
        this.demoRankingResource = demoRankingResource;
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
        var file = readJson(tracksResource, SeedData.TracksFile.class);
        for (var trackData : file.tracks()) {
            persistTrack(trackData);
        }
    }

    // 트랙을 먼저 저장해 ID 를 받은 뒤, 그 ID 를 참조하는 챕터/스텝을 차례로 저장한다.
    private void persistTrack(SeedData.Track trackData) {
        var track = trackRepository.save(Track.create(
                trackData.title(),
                trackData.description(),
                trackData.displayOrder()
        ));
        for (var chapterData : trackData.chapters()) {
            var chapter = scriptRepository.save(Script.createChapter(
                    track,
                    chapterData.chapterOrder(),
                    chapterData.title(),
                    chapterData.content(),
                    Difficulty.valueOf(chapterData.difficulty()),
                    chapterData.practiceWord(),
                    chapterData.masteryBadgeName()
            ));
            var steps = new ArrayList<LearningStep>(chapterData.steps().size());
            for (var stepData : chapterData.steps()) {
                steps.add(toEntity(chapter, stepData));
            }
            stepRepository.saveAll(steps);
        }
    }

    private LearningStep toEntity(Script chapter, SeedData.Step stepData) {
        var kind = StepKind.valueOf(stepData.kind());
        return switch (kind) {
            case INTRO -> LearningStep.intro(chapter, stepData.orderIndex(), stepData.prompt());
            case RECORD -> LearningStep.record(
                    chapter,
                    stepData.orderIndex(),
                    stepData.prompt(),
                    stepData.targetText()
            );
        };
    }

    // 시연용 가짜 사용자. 실제 사용자가 충분히 쌓이면 demo_ranking_entries 테이블을 비워서 끄면 된다.
    private void seedDemoRankingIfEmpty() {
        if (demoRankingRepository.count() > 0) {
            return;
        }
        var file = readJson(demoRankingResource, SeedData.DemoRankingFile.class);
        var entries = new ArrayList<DemoRankingEntry>(file.entries().size());
        for (var entry : file.entries()) {
            entries.add(DemoRankingEntry.of(entry.nickname(), entry.accuracy()));
        }
        demoRankingRepository.saveAll(entries);
    }

    // 파일이 없거나 형식이 깨지면 부팅을 막는다. 잘못된 시드로 서비스를 띄우는 쪽이 더 위험하기 때문.
    private <T> T readJson(Resource resource, Class<T> type) {
        try (var in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "시드 파일을 읽을 수 없습니다: " + resource.getDescription(), e);
        }
    }
}
