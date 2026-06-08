package com.capstoneecho.echo_back.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent singleEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

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

    @Test
    @DisplayName("BusinessException 4xx → WARN 한 줄, 스택트레이스 없음, 요청 컨텍스트 포함")
    void logsBusiness4xxAsWarnWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/__test/business/USER_NOT_FOUND"))
                .andExpect(status().isNotFound());

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("request_failed")
                .contains("method=GET")
                .contains("uri=/__test/business/USER_NOT_FOUND")
                .contains("userId=-")
                .contains("code=USER_NOT_FOUND")
                .contains("status=404");
    }

    @Test
    @DisplayName("BusinessException 5xx → ERROR 한 줄, 스택트레이스 포함")
    void logsBusiness5xxAsErrorWithStackTrace() throws Exception {
        mockMvc.perform(get("/__test/business/MODEL_SERVER_UNAVAILABLE"))
                .andExpect(status().isServiceUnavailable());

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getFormattedMessage())
                .contains("code=MODEL_SERVER_UNAVAILABLE")
                .contains("status=503");
    }

    @Test
    @DisplayName("예상치 못한 Exception → ERROR 한 줄, 스택트레이스 포함")
    void logsUnexpectedAsErrorWithStackTrace() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError());

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getFormattedMessage())
                .contains("code=INTERNAL_ERROR")
                .contains("status=500")
                .contains("ex=IllegalStateException");
    }

    @Test
    @DisplayName("@Valid 위반 → WARN 한 줄, 스택트레이스 없음, 필드명 포함")
    void logsValidationFailureAsWarnWithFieldInfo() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("code=VALIDATION_FAILED")
                .contains("status=400")
                .contains("name");
    }

    @Test
    @DisplayName("MaxUploadSize 초과 → WARN 한 줄, status=413")
    void logsMaxUploadAsWarnWith413() throws Exception {
        mockMvc.perform(get("/__test/multipart-overflow"))
                .andExpect(status().isPayloadTooLarge());

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("code=INVALID_REQUEST")
                .contains("status=413");
    }

    @Test
    @DisplayName("X-Forwarded-For 가 있으면 첫 홉을 clientIp 로 기록")
    void logsClientIpFromXForwardedForFirstHop() throws Exception {
        mockMvc.perform(get("/__test/business/USER_NOT_FOUND")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isNotFound());

        assertThat(singleEvent().getFormattedMessage()).contains("clientIp=203.0.113.7");
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
