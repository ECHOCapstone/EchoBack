package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.PromptResponse;
import com.capstoneecho.echo_back.external.llm.PromptCatalog;
import java.util.List;
import org.springframework.stereotype.Service;

// 프롬프트 조회/재정의/초기화. 실제 저장과 effective 본문 관리는 PromptCatalog 가 맡는다.
@Service
public class PromptAdminService {

    private final PromptCatalog promptCatalog;

    public PromptAdminService(PromptCatalog promptCatalog) {
        this.promptCatalog = promptCatalog;
    }

    public List<PromptResponse> list() {
        return promptCatalog.list().stream().map(PromptResponse::from).toList();
    }

    public PromptResponse get(String key) {
        return PromptResponse.from(promptCatalog.get(key));
    }

    public PromptResponse override(String key, String content) {
        return PromptResponse.from(promptCatalog.override(key, content));
    }

    public PromptResponse reset(String key) {
        return PromptResponse.from(promptCatalog.reset(key));
    }
}
