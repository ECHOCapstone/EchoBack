package com.capstoneecho.echo_back.system;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("GET /api/health → 200 + ApiResponse{UP, service, ISO-8601 UTC timestamp} + REST Docs 스니펫 생성")
    void returnsUpEnvelopeAndDocumentsSnippets() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("echo-app-backend"))
                .andExpect(jsonPath("$.data.timestamp").value(
                        org.hamcrest.Matchers.matchesRegex(
                                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(result -> {
                    String timestamp = com.jayway.jsonpath.JsonPath.read(
                            result.getResponse().getContentAsString(), "$.data.timestamp");
                    OffsetDateTime parsed = OffsetDateTime.parse(
                            timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    assertThat(parsed.getOffset().getTotalSeconds())
                            .as("timestamp must be UTC (Z offset)").isZero();
                })
                .andDo(document("system/get-health"));

        File snippetDir = new File("build/generated-snippets/system/get-health");
        assertThat(snippetDir).as("REST Docs snippet directory must exist").exists();
        assertThat(snippetDir.list())
                .as("expected at least 6 REST Docs snippet files in %s", snippetDir)
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(6);
    }
}
