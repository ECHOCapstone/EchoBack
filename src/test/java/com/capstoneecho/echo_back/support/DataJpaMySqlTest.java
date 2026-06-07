package com.capstoneecho.echo_back.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정된 datasource 를 그대로 사용하는 {@link DataJpaTest} 변형.
 *
 * <p>로컬: {@code application-test.yaml} 의 H2(MODE=MySQL) / CI: {@code application-ci.yaml} 의
 * Testcontainers MySQL. Spring Boot 기본 임베디드 DB 교체({@link AutoConfigureTestDatabase.Replace#ANY})
 * 를 끄고({@link AutoConfigureTestDatabase.Replace#NONE}) {@code test} 프로파일을 활성화한다.
 *
 * <p>엔티티의 MySQL 전용 {@code columnDefinition}(예: {@code TEXT},
 * {@code TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP}) 이 일반 모드 H2 에서 DDL 오류를 일으키던 문제를
 * MySQL 호환 모드로 해소한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public @interface DataJpaMySqlTest {
}
