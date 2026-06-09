package com.capstoneecho.echo_back.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 테스트 프로파일에서 Logback 로깅이 결정적으로 동작하는지 검증하는 진단/회귀 테스트.
 *
 * <p>이 클래스는 의도적으로 <b>순수 단위 테스트</b>다 — Spring 컨텍스트도 Mockito 도 쓰지 않는다.
 * Spring 컨텍스트가 없으면 Spring Boot 의 {@code LoggingApplicationListener} 가 동작하지 않아
 * {@code application-*.yaml} 의 {@code logging.level.*} 가 적용되지 않는다. 따라서 이 환경에서의
 * 로깅 동작은 전적으로 {@code src/test/resources/logback-test.xml} (SSOT) 가 책임진다.
 *
 * <p>logback-test.xml 이 없거나 레벨이 바뀌면 아래 단언이 깨지므로, 테스트 로깅 설정에 대한
 * 회귀 방지 역할을 한다.
 */
class TestLoggingConfigurationTest {

  /** 애플리케이션 패키지({@code com.capstoneecho.*}) 하위의 임의 로거. */
  private static final Logger APP_LOGGER =
      (Logger) LoggerFactory.getLogger("com.capstoneecho.echo_back.sample");

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    APP_LOGGER.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    APP_LOGGER.detachAppender(appender);
    appender.stop();
  }

  @Test
  void appLoggerEmitsDebugAndInfoInPureUnitTest() {
    APP_LOGGER.debug("debug-marker");
    APP_LOGGER.info("info-marker");

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .contains("debug-marker", "info-marker");
  }

  @Test
  void appLoggerEffectiveLevelIsDebugAndRootIsInfo() {
    assertThat(APP_LOGGER.getEffectiveLevel()).isEqualTo(Level.DEBUG);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    assertThat(root.getLevel()).isEqualTo(Level.INFO);
  }
}
