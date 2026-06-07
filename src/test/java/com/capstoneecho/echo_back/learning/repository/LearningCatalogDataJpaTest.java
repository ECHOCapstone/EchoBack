package com.capstoneecho.echo_back.learning.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.support.DataJpaMySqlTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaMySqlTest
class LearningCatalogDataJpaTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private LearningStepRepository learningStepRepository;

    @Test
    @DisplayName("findAllByOrderByDisplayOrderAsc 는 displayOrder 오름차순으로 정렬")
    void tracksOrderedByDisplayOrder() {
        trackRepository.save(Track.create("B", "b-desc", 2));
        trackRepository.save(Track.create("A", "a-desc", 1));
        trackRepository.save(Track.create("C", "c-desc", 3));

        List<Track> tracks = trackRepository.findAllByOrderByDisplayOrderAsc();

        assertThat(tracks).extracting(Track::getTitle).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("findByTrack_IdOrderByChapterOrderAsc 는 track 내 chapterOrder 오름차순")
    void scriptsOrderedByChapterOrderWithinTrack() {
        Track track = trackRepository.save(Track.create("Track1", "desc", 1));
        scriptRepository.save(
                Script.createChapter(track, 3, "Ch3", "c", Difficulty.HARD, null, null));
        scriptRepository.save(
                Script.createChapter(track, 1, "Ch1", "c", Difficulty.EASY, null, null));
        scriptRepository.save(
                Script.createChapter(track, 2, "Ch2", "c", Difficulty.MEDIUM, null, null));

        List<Script> scripts = scriptRepository.findByTrack_IdOrderByChapterOrderAsc(track.getId());

        assertThat(scripts).extracting(Script::getTitle).containsExactly("Ch1", "Ch2", "Ch3");
    }

    @Test
    @DisplayName("findByPresetTrueOrderByIdAsc 는 preset=true 만 반환")
    void findByPresetTrueOrdered() {
        Track track = trackRepository.save(Track.create("Track1", "desc", 1));
        scriptRepository.save(
                Script.createChapter(track, 1, "PresetA", "c", Difficulty.EASY, null, null));
        scriptRepository.save(Script.createStandalone("StandaloneA", "c", Difficulty.EASY));
        scriptRepository.save(
                Script.createChapter(track, 2, "PresetB", "c", Difficulty.EASY, null, null));

        List<Script> presets = scriptRepository.findByPresetTrueOrderByIdAsc();

        assertThat(presets).extracting(Script::getTitle).containsExactly("PresetA", "PresetB");
        assertThat(presets).allMatch(Script::isPreset);
    }

    @Test
    @DisplayName("findByScript_IdOrderByIdAsc 는 같은 script 의 LearningStep 을 id 오름차순으로 반환")
    void learningStepsOrderedById() {
        Track track = trackRepository.save(Track.create("Track1", "desc", 1));
        Script script = scriptRepository.save(
                Script.createChapter(track, 1, "Ch1", "hello", Difficulty.EASY, null, null));
        Script other = scriptRepository.save(
                Script.createChapter(track, 2, "Ch2", "world", Difficulty.EASY, null, null));

        learningStepRepository.save(LearningStep.intro(script, "안내"));
        learningStepRepository.save(LearningStep.record(script, "발음하세요", "hello"));
        learningStepRepository.save(LearningStep.intro(other, "다른 스크립트 안내"));

        List<LearningStep> steps = learningStepRepository.findByScript_IdOrderByIdAsc(script.getId());

        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(LearningStep::getKind)
                .containsExactly(StepKind.INTRO, StepKind.RECORD);
    }
}
