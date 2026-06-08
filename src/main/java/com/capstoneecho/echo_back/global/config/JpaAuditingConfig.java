package com.capstoneecho.echo_back.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// JPA Auditing 활성화를 별도 @Configuration 으로 분리한다.
// 메인 @SpringBootApplication 클래스에 두면 @WebMvcTest 같은 slice 가 끌어와
// JPA metamodel 이 비어 있는 컨텍스트에서 'JPA metamodel must not be empty' 폭발을 일으킨다.
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
