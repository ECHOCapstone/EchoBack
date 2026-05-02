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

// 부팅 시 빈 H2 DB 에 트랙·챕터·스텝과 데모 랭킹 엔트리를 채워 넣는 단발성 초기화기.
//
// 시드 데이터의 단일 진실 원천은 src/main/resources/seed/*.json 파일이다.
// 본 클래스는 JSON ↔ 도메인 엔티티 변환과 영속화의 책임만 가진다 (데이터 ↔ 코드 분리).
// 새 트랙/챕터/스텝 추가는 JSON 만 수정하면 되며, 빌드/재배포 외에 코드 변경이 필요 없다.
//
// 동일 시드의 중복 삽입은 각 영역이 비어 있을 때만 채우는 정책으로 막는다.
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

    // 한 트랙 + 그 챕터들 + 챕터의 스텝들을 한 번에 영속화한다. 트랙은 먼저 저장되어 ID 가 부여되고,
    // 챕터/스텝은 트랙·챕터 참조를 가진 채 일괄 저장된다.
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

    // 시드 step 한 항목을 도메인 엔티티로 변환한다. INTRO 는 prompt 만, RECORD 는 prompt + targetText
    // + canonicalPhonemes 까지 보유한다.
    private LearningStep toEntity(Script chapter, SeedData.Step stepData) {
        var kind = StepKind.valueOf(stepData.kind());
        return switch (kind) {
            case INTRO -> LearningStep.intro(chapter, stepData.orderIndex(), stepData.prompt());
            case RECORD -> LearningStep.record(
                    chapter,
                    stepData.orderIndex(),
                    stepData.prompt(),
                    stepData.targetText(),
                    stepData.canonicalPhonemes()
            );
        };
    }

    // 시연 단계의 가짜 사용자. 실제 PronunciationFeedback 누적이 충분해질 때까지의 임시 데이터로,
    // 운영 전환 시 demo_ranking_entries 테이블을 비우면 자동으로 사라진다.
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

    // 시드 JSON 을 매핑 record 로 디코드한다. 파일이 없거나 형식이 깨지면 부팅 자체를 실패시키는 게
    // 안전하다 — 잘못된 시드 위에서 서비스를 띄우는 쪽이 더 위험.
    private <T> T readJson(Resource resource, Class<T> type) {
        try (var in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "시드 파일을 읽을 수 없습니다: " + resource.getDescription(), e);
        }
    }
}
