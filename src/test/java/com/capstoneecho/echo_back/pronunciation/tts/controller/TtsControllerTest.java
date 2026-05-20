package com.capstoneecho.echo_back.pronunciation.tts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TtsControllerTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("POST /api/tts → 200 + audio/mpeg + non-empty MP3 with ID3 magic + REST Docs 스니펫")
    void returnsNonEmptyMp3WithId3Magic() throws Exception {
        String body = "{\"text\":\"Hello world.\",\"lang\":\"en\"}";

        MvcResult result = mockMvc.perform(post("/api/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andDo(document("tts/synthesize"))
                .andReturn();

        byte[] payload = result.getResponse().getContentAsByteArray();
        assertThat(payload).as("MP3 body must be non-empty").isNotEmpty();
        assertThat(payload.length).isGreaterThan(3);
        assertThat(new byte[] {payload[0], payload[1], payload[2]})
                .as("first 3 bytes must be ID3 magic")
                .containsExactly((byte) 0x49, (byte) 0x44, (byte) 0x33);

        assertSnippetCreated("tts/synthesize");
    }

    @Test
    @DisplayName("POST /api/tts text 공백 → 400 INVALID_REQUEST (ApiResponse 봉투)")
    void blankTextReturns400InvalidRequest() throws Exception {
        String body = "{\"text\":\"   \"}";

        mockMvc.perform(post("/api/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
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
