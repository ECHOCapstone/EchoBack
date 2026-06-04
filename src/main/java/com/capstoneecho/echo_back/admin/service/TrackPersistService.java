package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import com.capstoneecho.echo_back.global.seed.InitialDataLoader;
import com.capstoneecho.echo_back.global.seed.SeedData;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

// Track / Script / LearningStep 트리 전체를 외부 tracks.yaml 로 직렬화하고, 그 파일을 지워
// 공장 기본값(classpath:content/tracks.yaml) 으로 되돌리는 어드민 액션.
//
// 직렬화 순서는 InitialDataLoader 가 읽는 순서와 동일하게 정렬해 두어 영구 저장 후
// 어떤 시점에 시드 재적용해도 같은 ID/순서로 복원되도록 한다.
@Service
public class TrackPersistService implements PersistableSeed {

    private static final String DOMAIN = "tracks";

    private final TrackRepository trackRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository learningStepRepository;
    private final PersistableContentStore contentStore;
    private final InitialDataLoader initialDataLoader;
    private final ObjectMapper objectMapper;

    public TrackPersistService(
            TrackRepository trackRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            PersistableContentStore contentStore,
            InitialDataLoader initialDataLoader,
            ObjectMapper objectMapper
    ) {
        this.trackRepository = trackRepository;
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.contentStore = contentStore;
        this.initialDataLoader = initialDataLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public boolean supportsExplicitPersist() {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void persistCurrentStateToFile() {
        SeedData.TracksFile snapshot = buildSnapshot();
        contentStore.writeText(SeedFileLocations.TRACKS, toYaml(snapshot));
    }

    // "시드 재적용" 의 의미는 DB 를 비우고 시드 출처(우선순위: 영구 저장본 → classpath 기본값)에서 다시 로드.
    // 영구 저장본(.yaml) 은 절대 지우지 않는다 — 그걸 지우면 admin 이 영구 저장한 의도(부팅마다 자기 트리 복원)
    // 와 정반대 동작이 되고, 의도치 않게 git 공장 기본값으로 롤백된다.
    // 공장 기본값으로 진짜 되돌리고 싶으면 별도의 "공장 기본값 복원" 액션을 두는 것이 안전하다.
    @Override
    @Transactional
    public void resetToDefaults() {
        learningStepRepository.deleteAllInBatch();
        scriptRepository.deleteAllInBatch();
        trackRepository.deleteAllInBatch();
        initialDataLoader.seedTracksIfEmpty();
    }

    @Override
    public SeedFileStatus fileStatus() {
        return contentStore.statusOf(SeedFileLocations.TRACKS);
    }

    private SeedData.TracksFile buildSnapshot() {
        List<Track> tracks = trackRepository.findAllByOrderByDisplayOrderAsc();
        List<SeedData.Track> trackSnapshots = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            trackSnapshots.add(snapshotOf(track));
        }
        return new SeedData.TracksFile(trackSnapshots);
    }

    private SeedData.Track snapshotOf(Track track) {
        List<Script> chapters = scriptRepository.findByTrack_IdOrderByChapterOrderAsc(track.getId());
        List<SeedData.Chapter> chapterSnapshots = new ArrayList<>(chapters.size());
        for (Script chapter : chapters) {
            chapterSnapshots.add(snapshotOf(chapter));
        }
        return new SeedData.Track(
                track.getTitle(),
                track.getDescription(),
                track.getDisplayOrder(),
                chapterSnapshots
        );
    }

    private SeedData.Chapter snapshotOf(Script chapter) {
        List<LearningStep> steps = learningStepRepository.findByScript_IdOrderByIdAsc(chapter.getId());
        List<SeedData.Step> stepSnapshots = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            stepSnapshots.add(snapshotOf(steps.get(i), i));
        }
        return new SeedData.Chapter(
                chapter.getChapterOrder() == null ? 0 : chapter.getChapterOrder(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getDifficulty() == null ? null : chapter.getDifficulty().name(),
                chapter.getPracticeWord(),
                chapter.getMasteryBadgeName(),
                stepSnapshots
        );
    }

    private SeedData.Step snapshotOf(LearningStep step, int orderIndex) {
        return new SeedData.Step(
                orderIndex,
                step.getKind().name(),
                step.getPrompt(),
                step.getTargetText()
        );
    }

    private String toYaml(SeedData.TracksFile snapshot) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        Object tree = objectMapper.convertValue(snapshot, Object.class);
        return new Yaml(options).dump(tree);
    }
}
