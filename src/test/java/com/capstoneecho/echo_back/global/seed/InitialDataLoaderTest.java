package com.capstoneecho.echo_back.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * InitialDataLoader 자체는 @Profile("!test") 라 컨텍스트에 등록되지 않는다.
 * 이 테스트는 H2 가 떠 있는 test 프로필에서 리포지토리·ObjectMapper 만 주입받고
 * 로더를 수동으로 인스턴스화해 동작을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InitialDataLoaderTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private LearningStepRepository learningStepRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private InitialDataLoader loader;

    @BeforeEach
    void setUp() {
        // FK 순서대로 비운다: learning_steps -> scripts -> tracks
        learningStepRepository.deleteAllInBatch();
        scriptRepository.deleteAllInBatch();
        trackRepository.deleteAllInBatch();

        loader = new InitialDataLoader(
                trackRepository,
                scriptRepository,
                learningStepRepository,
                objectMapper,
                new ClassPathResource("content/test-tracks.yaml")
        );
    }

    @Test
    @DisplayName("비어 있는 DB 에서 실행하면 tracks.yaml 의 3개 트랙이 모두 시드된다")
    void run_emptyDb_seedsAllTracks() {
        loader.run(null);

        List<Track> tracks = trackRepository.findAllByOrderByDisplayOrderAsc();
        assertThat(tracks).hasSize(3);
        assertThat(tracks)
                .extracting(Track::getTitle)
                .containsExactly("기본 발음 트랙", "모음 구별 트랙", "일상 회화 표현");

        // 첫 트랙(기본 발음): 5개 챕터, 첫 챕터(잰말놀이)는 RECORD 1개 스텝
        Track basics = tracks.get(0);
        List<Script> basicChapters =
                scriptRepository.findByTrack_IdOrderByChapterOrderAsc(basics.getId());
        assertThat(basicChapters).hasSize(5);

        Script jaenmal = basicChapters.get(0);
        assertThat(jaenmal.getTitle()).isEqualTo("영어 잰말놀이");
        List<LearningStep> jaenmalSteps =
                learningStepRepository.findByScript_IdOrderByIdAsc(jaenmal.getId());
        assertThat(jaenmalSteps).hasSize(1);
        assertThat(jaenmalSteps.get(0).getKind()).isEqualTo(StepKind.RECORD);
    }

    @Test
    @DisplayName("이미 트랙이 있으면 시드 로더는 아무 것도 하지 않는다 (멱등)")
    void run_nonEmptyDb_isNoop() {
        trackRepository.save(Track.create("기존 트랙", "이미 있음", 99));
        long before = trackRepository.count();

        loader.run(null);

        assertThat(trackRepository.count()).isEqualTo(before);
        assertThat(scriptRepository.count()).isZero();
        assertThat(learningStepRepository.count()).isZero();
    }

    @Test
    @DisplayName("스텝은 yaml 의 orderIndex 순서로 저장되고 INTRO/RECORD 가 올바르게 매핑된다")
    void run_steps_orderedAndMappedByKind() {
        loader.run(null);

        // 두 번째 챕터(R vs L): INTRO, RECORD, RECORD, INTRO, RECORD, RECORD, INTRO, RECORD, RECORD
        Track basics = trackRepository.findAllByOrderByDisplayOrderAsc().get(0);
        Script rvl = scriptRepository.findByTrack_IdOrderByChapterOrderAsc(basics.getId()).get(1);
        assertThat(rvl.getTitle()).isEqualTo("발음 연습: R vs L");

        List<LearningStep> steps =
                learningStepRepository.findByScript_IdOrderByIdAsc(rvl.getId());
        assertThat(steps).hasSize(9);
        assertThat(steps).extracting(LearningStep::getKind).containsExactly(
                StepKind.INTRO, StepKind.RECORD, StepKind.RECORD,
                StepKind.INTRO, StepKind.RECORD, StepKind.RECORD,
                StepKind.INTRO, StepKind.RECORD, StepKind.RECORD
        );

        // INTRO 스텝은 targetText 가 비어 있어야 하고, RECORD 는 채워져 있어야 한다
        assertThat(steps.get(0).getTargetText()).isNull();
        assertThat(steps.get(1).getTargetText()).isEqualTo("R");
        assertThat(steps.get(2).getTargetText()).isEqualTo("L");
        assertThat(steps.get(4).getTargetText()).isEqualTo("Right");
    }
}
