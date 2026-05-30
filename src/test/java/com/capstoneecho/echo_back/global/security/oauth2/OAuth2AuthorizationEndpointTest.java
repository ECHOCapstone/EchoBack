package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// CustomOAuth2AuthorizationRequestResolver 가 등록한 GET /api/auth/oauth2/google/authorization 이
// Google 인증 URL 로 302 redirect 하는지, redirect_uri 쿼리에 새 콜백 경로가 들어가는지,
// 그리고 /api/auth/** permitAll 로 인증 없이 접근 가능한지를 검증한다.
class OAuth2AuthorizationEndpointTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("GET /api/auth/oauth2/google/authorization → 302 redirect to Google authorize endpoint + REST Docs 스니펫")
    void authorizeRedirectsToGoogle() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/google/authorization"))
                .andExpect(status().is3xxRedirection())
                .andDo(document("auth/get-oauth2-authorize-google"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("redirect Location header").isNotNull();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location).contains("client_id=test-google-client-id");
        assertThat(location).contains("response_type=code");
        // 공백은 URL encoding 으로 %20 또는 + 둘 다 가능하므로 둘 중 하나만 충족하면 통과.
        assertThat(location).matches(s -> s.contains("scope=openid") && s.contains("email"));
        assertThat(location).contains("state=");
        // redirect_uri 가 새 콜백 경로 (/api/auth/oauth2/google/callback) 로 빌드됐는지 확인.
        // URL-encoded 두 변형 모두 허용.
        assertThat(location).matches(s ->
                s.contains("redirect_uri=http%3A%2F%2Flocalhost%2Fapi%2Fauth%2Foauth2%2Fgoogle%2Fcallback")
                        || s.contains("redirect_uri=http://localhost/api/auth/oauth2/google/callback"));

        assertSnippetCreated("auth/get-oauth2-authorize-google");
    }

    @Test
    @DisplayName("GET /api/auth/oauth2/kakao/authorization → 302 redirect to Kakao authorize endpoint + REST Docs 스니펫")
    void authorizeRedirectsToKakao() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth2/kakao/authorization"))
                .andExpect(status().is3xxRedirection())
                .andDo(document("auth/get-oauth2-authorize-kakao"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("redirect Location header").isNotNull();
        assertThat(location).startsWith("https://kauth.kakao.com/oauth/authorize");
        assertThat(location).contains("client_id=test-kakao-client-id");
        assertThat(location).contains("response_type=code");
        // openid scope 가 포함된 OIDC 요청이므로 nonce 가 자동 첨부돼야 한다.
        assertThat(location).contains("nonce=");
        assertThat(location).contains("state=");
        // scope 의 공백 인코딩은 %20 / + 둘 다 허용. 카카오 1차 동의 항목 3 개가 모두 들어가야 한다.
        assertThat(location).contains("scope=");
        assertThat(location).contains("openid");
        assertThat(location).contains("profile_nickname");
        assertThat(location).contains("account_email");
        // redirect_uri 가 새 콜백 경로 (/api/auth/oauth2/kakao/callback) 로 빌드됐는지 확인.
        assertThat(location).matches(s ->
                s.contains("redirect_uri=http%3A%2F%2Flocalhost%2Fapi%2Fauth%2Foauth2%2Fkakao%2Fcallback")
                        || s.contains("redirect_uri=http://localhost/api/auth/oauth2/kakao/callback"));

        assertSnippetCreated("auth/get-oauth2-authorize-kakao");
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
