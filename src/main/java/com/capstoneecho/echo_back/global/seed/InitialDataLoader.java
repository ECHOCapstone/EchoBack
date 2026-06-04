package com.capstoneecho.echo_back.global.seed;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

// 부팅 시 tracks 테이블이 비어 있으면 tracks.yaml 을 읽어 Track → Script(chapter) → LearningStep
// 순으로 한 번만 채워 넣는다. 이미 데이터가 있으면 그대로 둔다 (멱등).
//
// 우선순위: 외부 ${app.content.dir}/tracks.yaml 이 있으면 그쪽 (어드민이 영구 저장한 버전),
//          없으면 classpath:content/tracks.yaml (git 으로 관리되는 공장 기본값) 으로 폴백.
// 어드민의 시드 재적용은 트랙을 전부 비우고 이 메서드를 다시 부른다.
@Component
@Profile("!test")
public class InitialDataLoader implements ApplicationRunner {

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository learningStepRepository;
    private final ObjectMapper objectMapper;
    private final Resource defaultTracksResource;
    private final PersistableContentStore contentStore;

    public InitialDataLoader(
            TrackRepository trackRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            ObjectMapper objectMapper,
            @Value("classpath:content/tracks.yaml") Resource defaultTracksResource,
            PersistableContentStore contentStore
    ) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.objectMapper = objectMapper;
        this.defaultTracksResource = defaultTracksResource;
        this.contentStore = contentStore;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTracksIfEmpty();
    }

    @Transactional
    public void seedTracksIfEmpty() {
        if (trackRepository.count() > 0) {
            return;
        }
        SeedData.TracksFile file = readEffectiveTracksFile();
        for (SeedData.Track trackData : file.tracks()) {
            persistTrack(trackData);
        }
    }

    // 외부 영구 저장본이 있으면 그쪽을 우선, 없으면 classpath 기본값을 읽는다.
    private SeedData.TracksFile readEffectiveTracksFile() {
        if (contentStore.exists("tracks.yaml")) {
            try (InputStream in = Files.newInputStream(contentStore.resolve("tracks.yaml"))) {
                Object tree = new Yaml().load(in);
                return objectMapper.convertValue(tree, SeedData.TracksFile.class);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "외부 시드 파일을 읽을 수 없습니다: " + contentStore.resolve("tracks.yaml"), e);
            }
        }
        return readYaml(defaultTracksResource, SeedData.TracksFile.class);
    }

    // 트랙을 먼저 저장해 ID 를 받은 뒤, 그 ID 를 참조하는 챕터/스텝을 차례로 저장한다.
    private void persistTrack(SeedData.Track trackData) {
        Track track = trackRepository.save(Track.create(
                trackData.title(),
                trackData.description(),
                trackData.displayOrder()
        ));
        for (SeedData.Chapter chapterData : trackData.chapters()) {
            // 영구 저장본의 difficulty 가 비었거나 (관리자 화면에서 비우고 저장한 경우) 알 수 없는 값이면
            // 시드 로딩 전체를 중단시키는 NPE/IllegalArgumentException 대신 MEDIUM 으로 폴백한다.
            // 한 챕터의 누락된 필드 때문에 전체 부팅이 막히는 것을 막는 안전망이다.
            Difficulty difficulty = parseDifficulty(chapterData.difficulty());
            Script chapter = scriptRepository.save(Script.createChapter(
                    track,
                    chapterData.chapterOrder(),
                    chapterData.title(),
                    chapterData.content(),
                    difficulty,
                    chapterData.practiceWord(),
                    chapterData.masteryBadgeName()
            ));
            // LearningStep 엔티티에는 orderIndex 컬럼이 없으므로,
            // yaml 의 orderIndex 로 정렬한 뒤 삽입 순서대로 저장한다.
            // findByScript_IdOrderByIdAsc 가 id 순으로 읽어가므로 의도한 순서가 유지된다.
            List<SeedData.Step> sortedSteps = new ArrayList<>(chapterData.steps());
            sortedSteps.sort(Comparator.comparingInt(SeedData.Step::orderIndex));
            List<LearningStep> steps = new ArrayList<>(sortedSteps.size());
            for (SeedData.Step stepData : sortedSteps) {
                steps.add(toEntity(chapter, stepData));
            }
            learningStepRepository.saveAll(steps);
        }
    }

    private static Difficulty parseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return Difficulty.MEDIUM;
        }
    }

    private LearningStep toEntity(Script chapter, SeedData.Step stepData) {
        StepKind kind = StepKind.valueOf(stepData.kind());
        return switch (kind) {
            case INTRO -> LearningStep.intro(chapter, stepData.prompt());
            case RECORD -> LearningStep.record(chapter, stepData.prompt(), stepData.targetText());
        };
    }

    // SnakeYAML 로 yaml 을 Map 트리로 읽은 뒤 ObjectMapper 로 레코드에 매핑한다.
    // (Spring 이 이미 의존하는 SnakeYAML 을 재사용 — 별도 jackson-yaml 의존성 불필요.)
    // 시드 파일이 없거나 파싱 실패면 잘못된 상태로 띄우는 것보다 부팅을 막는 편이 안전하다.
    private <T> T readYaml(Resource resource, Class<T> type) {
        try (var in = resource.getInputStream()) {
            Object tree = new Yaml().load(in);
            return objectMapper.convertValue(tree, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "시드 파일을 읽을 수 없습니다: " + resource.getDescription(), e);
        }
    }
}
