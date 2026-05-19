package com.capstoneecho.echo_back.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 통합 테스트 공통 베이스.
 *
 * <p>전체 Spring 컨텍스트 + MockMvc + Spring REST Docs 를 묶어 스니펫이
 * {@code build/generated-snippets} 으로 일관되게 생성되도록 한다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
public abstract class AbstractControllerIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}
