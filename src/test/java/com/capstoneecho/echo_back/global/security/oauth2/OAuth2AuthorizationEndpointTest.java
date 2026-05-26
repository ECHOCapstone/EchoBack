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

// Spring 이 자동 등록하는 GET /oauth2/authorization/google 가 Google 인증 URL 로 302 redirect 하는지,
// 그리고 permitAll 로 인증 없이 접근 가능한지를 검증한다.
class OAuth2AuthorizationEndpointTest extends AbstractControllerIntegrationTest {

    @Test
    @DisplayName("GET /oauth2/authorization/google → 302 redirect to Google authorize endpoint + REST Docs 스니펫")
    void authorizeRedirectsToGoogle() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
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

        assertSnippetCreated("auth/get-oauth2-authorize-google");
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
