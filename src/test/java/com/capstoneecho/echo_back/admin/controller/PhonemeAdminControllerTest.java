package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractAdminControllerIntegrationTest;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PhonemeAdminControllerTest extends AbstractAdminControllerIntegrationTest {

    // 1x1 PNG.
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAen63NgAAAAASUVORK5CYII=");

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("image", "r.png", "image/png", PNG);
    }

    @Test
    @DisplayName("POST /api/admin/phonemes/{p}/image ADMIN → 업로드 + imageUrl")
    void uploadImage() throws Exception {
        mockMvc.perform(multipart("/api/admin/phonemes/r/image")
                        .file(pngFile())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneme").value("R"))
                .andExpect(jsonPath("$.data.imageUrl").value("/api/phonemes/R/image"));
    }

    @Test
    @DisplayName("업로드 후 공개 GET /api/phonemes/{p}/image → 이미지 바이트 (인증 불필요)")
    void serveImagePublicly() throws Exception {
        mockMvc.perform(multipart("/api/admin/phonemes/TH/image")
                        .file(pngFile())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/phonemes/TH/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @DisplayName("GET /api/admin/phonemes → 등록 목록")
    void listAssets() throws Exception {
        mockMvc.perform(multipart("/api/admin/phonemes/AE/image")
                        .file(pngFile())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/phonemes").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.phoneme == 'AE')]").exists());
    }

    @Test
    @DisplayName("DELETE /api/admin/phonemes/{p} → 200")
    void deleteAsset() throws Exception {
        mockMvc.perform(multipart("/api/admin/phonemes/V/image")
                        .file(pngFile())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/phonemes/V").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("지원하지 않는 형식 → 400")
    void rejectsNonImage() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("image", "x.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/admin/phonemes/R/image")
                        .file(txt)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 음소 이미지 GET → 404")
    void missingImageReturns404() throws Exception {
        mockMvc.perform(get("/api/phonemes/ZZ/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PHONEME_ASSET_NOT_FOUND"));
    }

    @Test
    @DisplayName("USER 권한 업로드 → 403")
    void userForbidden() throws Exception {
        mockMvc.perform(multipart("/api/admin/phonemes/R/image")
                        .file(pngFile())
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }
}
