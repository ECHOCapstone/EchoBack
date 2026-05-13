package com.capstoneecho.echo_back.learning.track.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.Script;
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
class TrackControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("GET /api/tracks → 200 + displayOrder 오름차순 + chapterCount + REST Docs 스니펫")
    void listReturnsByDisplayOrder() throws Exception {
        Track alpha = trackRepository.save(Track.create("Alpha", "a-desc", 1));
        trackRepository.save(Track.create("Bravo", "b-desc", 2));
        trackRepository.save(Track.create("Charlie", "c-desc", 3));
        scriptRepository.save(
                Script.createChapter(alpha, 1, "Alpha-Ch1", "c1", Difficulty.EASY, "a", null));
        scriptRepository.save(
                Script.createChapter(alpha, 2, "Alpha-Ch2", "c2", Difficulty.MEDIUM, "b", null));

        String token = issueToken(savedUser("trackuser1", "trackuser1@test.com"));

        mockMvc.perform(get("/api/tracks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("Alpha"))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data[0].chapterCount").value(2))
                .andExpect(jsonPath("$.data[1].title").value("Bravo"))
                .andExpect(jsonPath("$.data[1].chapterCount").value(0))
                .andExpect(jsonPath("$.data[2].title").value("Charlie"))
                .andExpect(jsonPath("$.data[2].chapterCount").value(0))
                .andDo(document("tracks/list"));

        assertSnippetCreated("tracks/list");
    }

    @Test
    @DisplayName("GET /api/tracks/{id} → 200 + 챕터 포함 상세 + REST Docs 스니펫")
    void getReturnsDetailWithChapters() throws Exception {
        Track track = trackRepository.save(Track.create("Pronunciation Basics", "vowels and consonants", 1));
        scriptRepository.save(
                Script.createChapter(track, 2, "Long Vowels", "content-2", Difficulty.MEDIUM, "beach", null));
        scriptRepository.save(
                Script.createChapter(track, 1, "Short Vowels", "content-1", Difficulty.EASY, "cat", null));

        String token = issueToken(savedUser("trackuser2", "trackuser2@test.com"));

        mockMvc.perform(get("/api/tracks/{trackId}", track.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(track.getId()))
                .andExpect(jsonPath("$.data.title").value("Pronunciation Basics"))
                .andExpect(jsonPath("$.data.displayOrder").value(1))
                .andExpect(jsonPath("$.data.chapters.length()").value(2))
                .andExpect(jsonPath("$.data.chapters[0].scriptId").isNumber())
                .andExpect(jsonPath("$.data.chapters[0].title").value("Short Vowels"))
                .andExpect(jsonPath("$.data.chapters[0].chapterOrder").value(1))
                .andExpect(jsonPath("$.data.chapters[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.chapters[1].scriptId").isNumber())
                .andExpect(jsonPath("$.data.chapters[1].title").value("Long Vowels"))
                .andExpect(jsonPath("$.data.chapters[1].chapterOrder").value(2))
                .andDo(document("tracks/get"));

        assertSnippetCreated("tracks/get");
    }

    @Test
    @DisplayName("GET /api/tracks/{id} 미존재 → 404 TRACK_NOT_FOUND")
    void getUnknownTrackReturns404TrackNotFound() throws Exception {
        String token = issueToken(savedUser("trackuser3", "trackuser3@test.com"));

        mockMvc.perform(get("/api/tracks/{trackId}", 999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TRACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/tracks 토큰 없음 → 401 UNAUTHORIZED")
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tracks"))
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
