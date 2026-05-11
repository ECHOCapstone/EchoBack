package com.capstoneecho.echo_back.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("permitAll 경로(/api/auth/**, /api/health, /error, /actuator/health) 는 미인증 401 아님")
    void permitAllPathsAreOpen() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("/api/auth/** must be permitAll").isNotEqualTo(401));

        mockMvc.perform(get("/api/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("/api/health must be permitAll").isNotEqualTo(401));

        mockMvc.perform(get("/error"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("/error must be permitAll").isNotEqualTo(401));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/api/** 보호 경로는 미인증이면 401 + ApiResponse(UNAUTHORIZED) 봉투")
    void protectedPathsReturn401Envelope() throws Exception {
        mockMvc.perform(get("/api/some-protected-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"success\":false")))
                .andExpect(content().string(containsString("\"errorCode\":\"UNAUTHORIZED\"")));
    }

    @Test
    @DisplayName("CORS 설정이 적용되어 Authorization 헤더를 expose 한다")
    void corsExposesAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        containsString("Authorization")));
    }
}
