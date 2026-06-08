package com.capstoneecho.echo_back.pronunciation.phoneme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PhonemeControllerTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("GET /api/phonemes → 인벤토리 기반 조음 안내 39건(음차·설명·이미지경로) + permitAll")
    void listsArticulations() throws Exception {
        mockMvc.perform(get("/api/phonemes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(39))
                .andExpect(jsonPath("$.data[?(@.phoneme=='R')].koreanCue").value(Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data[?(@.phoneme=='R')].tip").value(Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data[?(@.phoneme=='R')].imageUrl")
                        .value(Matchers.hasItem("/api/phonemes/R/image")))
                .andDo(document("phoneme/list"));
    }

    @Test
    @DisplayName("GET /api/phonemes/{phoneme}/image → PNG 바이트 + 장기 캐시 헤더")
    void servesImageBytes() throws Exception {
        mockMvc.perform(get("/api/phonemes/{phoneme}/image", "R"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age")))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty())
                .andDo(document("phoneme/get-image"));
    }

    @Test
    @DisplayName("GET /api/phonemes/{phoneme}/image → 소문자 입력도 정규화해 동일 이미지 서빙")
    void normalizesPhonemeCase() throws Exception {
        mockMvc.perform(get("/api/phonemes/{phoneme}/image", "th"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("GET /api/phonemes/{phoneme}/image → 인벤토리에 없는 음소면 404")
    void unknownPhonemeReturns404() throws Exception {
        mockMvc.perform(get("/api/phonemes/{phoneme}/image", "ZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PHONEME_ASSET_NOT_FOUND"));
    }
}
