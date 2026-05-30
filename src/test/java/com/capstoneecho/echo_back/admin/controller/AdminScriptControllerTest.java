package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.admin.dto.AdminStepRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptCreateRequest;
import com.capstoneecho.echo_back.admin.service.AdminScriptService;
import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminScriptControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private AdminScriptService adminScriptService;

    private String adminToken() {
        return jwtProvider.issue(1L,
                Map.of("username", "admin", "email", "admin@test.com", "role", "ADMIN"));
    }

    private Track newTrack() {
        return trackRepository.save(Track.create("R 트랙", "설명", 1));
    }

    private ScriptDetailResponse seedScript(Track track) {
        return adminScriptService.create(new ScriptCreateRequest(
                track.getId(), 1, "챕터", "내용", Difficulty.EASY, null, null,
                List.of(new AdminStepRequest(StepKind.INTRO, "안내", null),
                        new AdminStepRequest(StepKind.RECORD, "따라 읽기", "hello"))));
    }

    @Test
    @DisplayName("POST /api/admin/scripts ADMIN → 챕터 생성 + 스텝")
    void createScript() throws Exception {
        Track track = newTrack();
        String body = """
                {"trackId": %d, "chapterOrder": 1, "title": "챕터1", "content": "내용",
                 "difficulty": "EASY", "steps": [
                   {"kind": "INTRO", "prompt": "안내"},
                   {"kind": "RECORD", "prompt": "읽기", "targetText": "hello"}]}
                """.formatted(track.getId());

        mockMvc.perform(post("/api/admin/scripts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("챕터1"))
                .andExpect(jsonPath("$.data.isPreset").value(true))
                .andExpect(jsonPath("$.data.steps.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/admin/scripts?trackId= → 트랙의 스크립트 목록")
    void listByTrack() throws Exception {
        Track track = newTrack();
        seedScript(track);

        mockMvc.perform(get("/api/admin/scripts").param("trackId", String.valueOf(track.getId()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/scripts/{id} → 상세 + 스텝")
    void getDetail() throws Exception {
        Track track = newTrack();
        ScriptDetailResponse created = seedScript(track);

        mockMvc.perform(get("/api/admin/scripts/" + created.id())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/admin/scripts/{id} → 본문/스텝 교체")
    void updateScript() throws Exception {
        Track track = newTrack();
        ScriptDetailResponse created = seedScript(track);
        String body = """
                {"chapterOrder": 2, "title": "바뀐 제목", "content": "새 내용",
                 "difficulty": "HARD", "steps": [
                   {"kind": "RECORD", "prompt": "읽기", "targetText": "world"}]}
                """;

        mockMvc.perform(put("/api/admin/scripts/" + created.id())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.data.steps.length()").value(1));
    }

    @Test
    @DisplayName("DELETE /api/admin/scripts/{id} → 200")
    void deleteScript() throws Exception {
        Track track = newTrack();
        ScriptDetailResponse created = seedScript(track);

        mockMvc.perform(delete("/api/admin/scripts/" + created.id())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 트랙에 스크립트 생성 → 404 TRACK_NOT_FOUND")
    void createUnderMissingTrack() throws Exception {
        String body = """
                {"trackId": 999999, "title": "x", "content": "y", "steps": [
                   {"kind": "INTRO", "prompt": "안내"}]}
                """;

        mockMvc.perform(post("/api/admin/scripts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("RECORD 스텝에 targetText 누락 → 400")
    void recordStepWithoutTargetRejected() throws Exception {
        Track track = newTrack();
        String body = """
                {"trackId": %d, "title": "x", "content": "y", "steps": [
                   {"kind": "RECORD", "prompt": "읽기"}]}
                """.formatted(track.getId());

        mockMvc.perform(post("/api/admin/scripts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("스텝 없는 생성 → 400 VALIDATION_FAILED")
    void createWithoutStepsRejected() throws Exception {
        Track track = newTrack();
        String body = """
                {"trackId": %d, "title": "x", "content": "y", "steps": []}
                """.formatted(track.getId());

        mockMvc.perform(post("/api/admin/scripts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET USER 권한 → 403")
    void userForbidden() throws Exception {
        String userToken = jwtProvider.issue(2L,
                Map.of("username", "u", "email", "u@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/scripts/1").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
