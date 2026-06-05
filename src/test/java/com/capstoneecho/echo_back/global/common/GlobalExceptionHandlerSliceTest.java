package com.capstoneecho.echo_back.global.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(
        controllers = GlobalExceptionHandlerSliceTest.TestEndpoints.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.capstoneecho.echo_back.global.config.WebMvcConfig.class,
                        com.capstoneecho.echo_back.global.security.CurrentUserArgumentResolver.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerSliceTest.TestEndpoints.class})
class GlobalExceptionHandlerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} maps to its declared HTTP status with envelope")
    @EnumSource(ErrorCode.class)
    @DisplayName("BusinessException → ApiResponse envelope (status + error.code + error.message)")
    void mapsBusinessExceptionToEnvelope(ErrorCode code) throws Exception {
        mockMvc.perform(get("/__test/business/" + code.name()))
                .andExpect(status().is(code.getStatusCode()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code.name()))
                .andExpect(jsonPath("$.error.message").value(code.getDefaultMessage()))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("@Valid 위반 → 400 + VALIDATION_FAILED")
    void mapsValidationFailureTo400() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException → 413 + INVALID_REQUEST")
    void mapsMultipartLimitExceededToPayloadTooLarge() throws Exception {
        mockMvc.perform(get("/__test/multipart-overflow"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.name()))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("일반 Exception → 500 + INTERNAL_ERROR")
    void mapsUnexpectedExceptionToInternalError() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERNAL_ERROR.name()))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/__test/business/{code}")
        public void throwBusiness(@PathVariable("code") String code) {
            throw new BusinessException(ErrorCode.valueOf(code));
        }

        @PostMapping("/__test/validate")
        public ApiResponse<String> validate(@Valid @RequestBody Payload payload) {
            return ApiResponse.success(payload.name());
        }

        @GetMapping("/__test/multipart-overflow")
        public void multipartOverflow() {
            throw new MaxUploadSizeExceededException(25_000_000L);
        }

        @GetMapping("/__test/boom")
        public void boom() {
            throw new IllegalStateException("unexpected");
        }
    }

    record Payload(@NotBlank String name) {}
}
