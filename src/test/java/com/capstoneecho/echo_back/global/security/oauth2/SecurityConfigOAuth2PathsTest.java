package com.capstoneecho.echo_back.global.security.oauth2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// SecurityConfig 의 OAuth2 경로 이전 (/api/auth/oauth2/{registrationId}/authorization,
// /api/auth/oauth2/{registrationId}/callback) 이 의도대로 적용됐는지,
// 옛 경로들이 더 이상 OAuth 흐름에 잡히지 않는지, /api/health 같은 공개 경로 회귀가
// 없는지를 가볍게 확인한다.
class SecurityConfigOAuth2PathsTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("새 시작 경로 /api/auth/oauth2/google/authorization 은 인증 없이 접근 가능하다 (302, 401 아님)")
    void oauth2AuthorizationIsPermitted() throws Exception {
        mockMvc.perform(get("/api/auth/oauth2/google/authorization"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("새 시작 경로 /api/auth/oauth2/kakao/authorization 도 인증 없이 접근 가능하다 (302, 401 아님)")
    void oauth2KakaoAuthorizationIsPermitted() throws Exception {
        mockMvc.perform(get("/api/auth/oauth2/kakao/authorization"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("옛 시작 경로 /oauth2/authorization/google 은 더 이상 redirect 하지 않는다 (path 이전 회귀 가드)")
    void legacyAuthorizationPathNoLongerActive() throws Exception {
        // permitAll 에서 제거됐고 custom resolver 도 안 잡으므로 401 (또는 4xx).
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("옛 콜백 경로 /login/oauth2/code/google 도 비활성화됐다 (path 이전 회귀 가드)")
    void legacyCallbackPathNoLongerActive() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google"))
                .andExpect(status().is4xxClientError());
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
