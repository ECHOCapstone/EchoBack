package com.capstoneecho.echo_back.support;

import com.capstoneecho.echo_back.external.llm.LlmComprehensiveFeedback;
import com.capstoneecho.echo_back.external.llm.LlmRetryFeedback;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PracticeItem;
import com.capstoneecho.echo_back.external.llm.PronunciationGuide;
import com.capstoneecho.echo_back.external.llm.WrongWord;
import java.util.List;

// LlmClient mock 응답 모음. score 는 더 이상 LLM 출력에 포함되지 않으므로 mock 도 score 필드를 갖지 않는다.
public final class LlmMockResponses {

    private LlmMockResponses() {
    }

    public static LlmStepFeedback defaultStep() {
        return new LlmStepFeedback(
                List.of(), List.of(), false,
                "발음을 더 또렷하게 따라 읽어 보세요.",
                PronunciationGuide.empty(),
                List.of(), List.of(), List.of(), List.of());
    }

    public static LlmStepFeedback stepWithWrongWord(String word, int index) {
        return new LlmStepFeedback(
                List.of(), List.of(), true,
                "ɔ 모음을 더 둥글게 발음해 보세요.",
                PronunciationGuide.empty(),
                List.of(), List.of(),
                List.of(new WrongWord(word, index)),
                List.of());
    }

    public static LlmRetryFeedback defaultRetry() {
        return new LlmRetryFeedback(
                List.of(), List.of(), true, false,
                "잘 했어요.", PronunciationGuide.empty(), List.of());
    }

    public static LlmComprehensiveFeedback defaultComprehensive() {
        return new LlmComprehensiveFeedback(
                85,
                "전반적으로 좋은 발음이었어요.",
                List.of(),
                List.of(),
                List.of(new PracticeItem("the", PracticeItem.Kind.WORD, "기본 단어")));
    }
}
