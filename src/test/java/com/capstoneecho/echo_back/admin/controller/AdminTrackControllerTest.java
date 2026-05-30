package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminTrackControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TrackRepository trackRepository;

    private String adminToken() {
        return jwtProvider.issue(1L,
                Map.of("username", "admin", "email", "admin@test.com", "role", "ADMIN"));
    }

    @Test
    @DisplayName("POST /api/admin/tracks ADMIN → 201 의미의 200 + 생성된 트랙")
    void createTrack() throws Exception {
        mockMvc.perform(post("/api/admin/tracks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"새 트랙\", \"description\": \"설명\", \"displayOrder\": 9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("새 트랙"))
                .andExpect(jsonPath("$.data.chapterCount").value(0));
    }

    @Test
    @DisplayName("GET /api/admin/tracks ADMIN → 목록 배열")
    void listTracks() throws Exception {
        mockMvc.perform(get("/api/admin/tracks").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PUT /api/admin/tracks/{id} ADMIN → 제목 갱신")
    void updateTrack() throws Exception {
        Track track = trackRepository.save(Track.create("이전 제목", "d", 1));

        mockMvc.perform(put("/api/admin/tracks/" + track.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"바뀐 제목\", \"description\": \"d2\", \"displayOrder\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.data.displayOrder").value(2));
    }

    @Test
    @DisplayName("DELETE /api/admin/tracks/{id} 빈 트랙 → 200")
    void deleteEmptyTrack() throws Exception {
        Track track = trackRepository.save(Track.create("지울 트랙", "d", 1));

        mockMvc.perform(delete("/api/admin/tracks/" + track.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST 빈 제목 → 400 VALIDATION_FAILED")
    void createBlankTitleRejected() throws Exception {
        mockMvc.perform(post("/api/admin/tracks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"  \", \"displayOrder\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/admin/tracks USER 권한 → 403")
    void userForbidden() throws Exception {
        String userToken = jwtProvider.issue(2L,
                Map.of("username", "u", "email", "u@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/tracks").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
