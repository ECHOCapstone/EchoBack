package com.capstoneecho.echo_back.learning.script.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ScriptControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private LearningStepRepository learningStepRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("GET /api/scripts/{id} → 200 + steps 포함 상세 + REST Docs 스니펫")
    void getReturnsDetailWithSteps() throws Exception {
        Track track = trackRepository.save(Track.create("Pronunciation", "desc", 1));
        Script script = scriptRepository.save(
                Script.createChapter(track, 1, "Long Vowels", "the long vowels here",
                        Difficulty.MEDIUM, "beach", "VowelMaster"));
        learningStepRepository.save(LearningStep.intro(script, "Listen carefully"));
        learningStepRepository.save(
                LearningStep.record(script, "Repeat after me", "the long vowels here"));

        String token = issueToken(savedUser("scriptuser1", "scriptuser1@test.com"));

        mockMvc.perform(get("/api/scripts/{scriptId}", script.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(script.getId()))
                .andExpect(jsonPath("$.data.title").value("Long Vowels"))
                .andExpect(jsonPath("$.data.content").value("the long vowels here"))
                .andExpect(jsonPath("$.data.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.data.practiceWord").value("beach"))
                .andExpect(jsonPath("$.data.masteryBadgeName").value("VowelMaster"))
                .andExpect(jsonPath("$.data.isPreset").value(true))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].kind").value("INTRO"))
                .andExpect(jsonPath("$.data.steps[0].prompt").value("Listen carefully"))
                .andExpect(jsonPath("$.data.steps[1].kind").value("RECORD"))
                .andExpect(jsonPath("$.data.steps[1].targetText").value("the long vowels here"))
                .andDo(document("scripts/get"));

        assertSnippetCreated("scripts/get");
    }

    @Test
    @DisplayName("GET /api/scripts/{id} 미존재 → 404 SCRIPT_NOT_FOUND")
    void getUnknownScriptReturns404ScriptNotFound() throws Exception {
        String token = issueToken(savedUser("scriptuser2", "scriptuser2@test.com"));

        mockMvc.perform(get("/api/scripts/{scriptId}", 999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SCRIPT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/scripts/{id} 스텝 0개 → 404 STEP_NOT_FOUND")
    void getScriptWithoutStepsReturns404StepNotFound() throws Exception {
        Script script = scriptRepository.save(
                Script.createStandalone("Empty", "no steps yet", Difficulty.EASY));
        String token = issueToken(savedUser("scriptuser3", "scriptuser3@test.com"));

        mockMvc.perform(get("/api/scripts/{scriptId}", script.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("STEP_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/scripts/recommended/today → 200 + preset 결정론 추천 + REST Docs 스니펫")
    void recommendedTodayReturnsDeterministicList() throws Exception {
        for (int i = 0; i < 6; i++) {
            Track track = trackRepository.save(Track.create("T-" + i, "d-" + i, i + 1));
            scriptRepository.save(
                    Script.createChapter(
                            track,
                            1,
                            "Title-" + i,
                            "content-" + i,
                            Difficulty.EASY,
                            "word-" + i,
                            null));
        }

        String token = issueToken(savedUser("scriptuser4", "scriptuser4@test.com"));

        mockMvc.perform(get("/api/scripts/recommended/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].isPreset").value(true))
                .andDo(document("scripts/recommended-today"));

        assertSnippetCreated("scripts/recommended-today");
    }

    @Test
    @DisplayName("GET /api/scripts/{id} 토큰 없음 → 401 UNAUTHORIZED")
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/scripts/{scriptId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User savedUser(String username, String email) {
        return userRepository.save(User.signup(
                username, email, passwordEncoder.encode("Password1!"), "Nick"));
    }

    private String issueToken(User user) {
        return jwtProvider.issue(
                user.getId(),
                Map.of("username", user.getUsername(), "email", user.getEmail()));
    }

    private static void assertSnippetCreated(String snippetId) {
        File dir = new File("build/generated-snippets/" + snippetId);
        assertThat(dir).as("REST Docs snippet dir must exist: %s", dir).exists();
        assertThat(dir.list())
                .as("expected REST Docs snippet files in %s", dir)
                .isNotNull()
                .isNotEmpty();
    }
}
