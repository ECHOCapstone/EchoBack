package com.capstoneecho.echo_back.external.modelserver.support;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

// 모델 서버 호출 실패를 경계에서 먼저 로깅하고 BusinessException 으로 매핑하는 단일 출처.
// 로그 캡처는 기존 GlobalExceptionHandlerSliceTest 와 동일한 Logback ListAppender 패턴을 쓴다.
class ModelServerErrorTranslatorTest {

    private final ModelServerErrorTranslator translator = new ModelServerErrorTranslator();

    private Logger translatorLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        translatorLogger = (Logger) LoggerFactory.getLogger(ModelServerErrorTranslator.class);
        appender = new ListAppender<>();
        appender.start();
        translatorLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        translatorLogger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent singleEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    @Test
    @DisplayName("unavailable: 연결/타임아웃을 MODEL_SERVER_UNAVAILABLE 로 매핑하고 upstream 스택트레이스와 함께 ERROR 로깅")
    void unavailableMapsAndLogsWithStackTrace() {
        ResourceAccessException upstream =
                new ResourceAccessException("connect timed out", new SocketTimeoutException("timeout"));

        BusinessException result = translator.unavailable("/transcribe", upstream);

        assertThat(result.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE);

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getFormattedMessage())
                .contains("모델 서버 호출 실패")
                .contains("endpoint=/transcribe")
                .contains("MODEL_SERVER_UNAVAILABLE")
                .contains("ResourceAccessException")
                .contains("connect timed out");
    }

    @Test
    @DisplayName("responseError: upstream 4xx/5xx 응답을 MODEL_SERVER_ERROR 로 매핑하고 status·body 를 ERROR 로깅")
    void responseErrorMapsAndLogsStatusAndBody() {
        RestClientResponseException upstream = new RestClientResponseException(
                "500 Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                new HttpHeaders(),
                "boom".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        BusinessException result = translator.responseError("/g2p", upstream);

        assertThat(result.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR);

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("endpoint=/g2p")
                .contains("MODEL_SERVER_ERROR")
                .contains("status=500")
                .contains("boom");
    }

    @Test
    @DisplayName("responseError: 과도하게 긴 body 는 절단되어 로깅된다")
    void responseErrorTruncatesLongBody() {
        String longBody = "x".repeat(600);
        RestClientResponseException upstream = new RestClientResponseException(
                "502 Bad Gateway",
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                new HttpHeaders(),
                longBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        translator.responseError("/transcribe", upstream);

        String message = singleEvent().getFormattedMessage();
        assertThat(message).contains("…");
        assertThat(message).doesNotContain("x".repeat(600));
    }

    @Test
    @DisplayName("emptyResponse: 모델 서버 빈 응답을 MODEL_SERVER_ERROR 로 매핑하고 스택트레이스 없이 ERROR 로깅")
    void emptyResponseMapsAndLogsWithoutStackTrace() {
        BusinessException result = translator.emptyResponse("/g2p", "empty /g2p response");

        assertThat(result.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_ERROR);
        assertThat(result.getMessage()).contains("empty /g2p response");

        ILoggingEvent event = singleEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("endpoint=/g2p")
                .contains("MODEL_SERVER_ERROR")
                .contains("empty /g2p response");
    }
}
