package com.capstoneecho.echo_back.global.seed;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// 부팅 시 tracks 테이블이 비어 있으면 classpath:seed/tracks.json 을 읽어
// Track -> Script(chapter) -> LearningStep 순으로 한 번만 채워 넣는다.
// 이미 행이 있으면 그대로 둔다 (멱등). 테스트 프로파일은 픽스처와 간섭을 막기 위해 비활성화한다.
@Component
@Profile("!test")
public class InitialDataLoader implements ApplicationRunner {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final Resource tracksResource;

    public InitialDataLoader(
            TrackRepository trackRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository stepRepository,
            ObjectMapper objectMapper,
            @Value("classpath:seed/tracks.json") Resource tracksResource
    ) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
        this.tracksResource = tracksResource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!trackRepository.findAll().isEmpty()) {
            return;
        }
        var file = readJson(tracksResource, SeedData.TracksFile.class);
        for (var trackData : file.tracks()) {
            persistTrack(trackData);
        }
    }

    // 트랙을 먼저 저장해 ID 를 받은 뒤, 그 ID 를 참조하는 챕터 / 스텝을 차례로 저장한다.
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

    // 시드 파일이 없거나 형식이 깨지면 부팅을 막는다. 잘못된 시드로 서비스를 띄우는 쪽이 더 위험하다.
    private <T> T readJson(Resource resource, Class<T> type) {
        try (var in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "시드 파일을 읽을 수 없습니다: " + resource.getDescription(), e);
        }
    }
}
