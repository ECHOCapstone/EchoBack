package com.capstoneecho.echo_back.global.security.oauth2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// SecurityConfig 의 permitAll 경로 변경 (oauth2/**, login/oauth2/**) 이 의도대로 적용됐는지,
// 기존 /api/health 같은 공개 경로 회귀가 없는지를 가볍게 확인한다.
class SecurityConfigOAuth2PathsTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("/oauth2/authorization/google 은 인증 없이 접근 가능하다 (302, 401 아님)")
    void oauth2AuthorizationIsPermitted() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("/api/health 회귀 없음 — 여전히 200")
    void healthEndpointStillPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호된 /api 경로는 토큰 없으면 여전히 401")
    void protectedApiStillUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }
}
