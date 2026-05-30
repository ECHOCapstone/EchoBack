package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.external.llm.PromptCatalog;

// 프롬프트 한 건의 현재 상태. overridden 이 true 면 classpath 기본값이 아닌 재정의 본문이다.
public record PromptResponse(String key, String content, boolean overridden) {

    public static PromptResponse from(PromptCatalog.PromptView view) {
        return new PromptResponse(view.key(), view.content(), view.overridden());
    }
}
