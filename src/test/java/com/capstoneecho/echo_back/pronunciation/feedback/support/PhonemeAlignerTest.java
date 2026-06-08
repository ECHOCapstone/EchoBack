package com.capstoneecho.echo_back.pronunciation.feedback.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.AlignmentOp.ErrorType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 결정적 정렬기 검증 — 편집거리 정렬이 MATCH/SUB/INS/DEL 을 올바르게 만든다.
class PhonemeAlignerTest {

    @Test
    @DisplayName("완전 일치는 모두 MATCH")
    void allMatch() {
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("HH", "AH", "L", "OW"),
                List.of("HH", "AH", "L", "OW"));
        assertThat(ops).extracting(AlignmentOp::errorType).containsExactly(
                ErrorType.MATCH, ErrorType.MATCH, ErrorType.MATCH, ErrorType.MATCH);
    }

    @Test
    @DisplayName("치환: growth(TH) 를 S 로 발음 → 해당 위치만 SUBSTITUTION")
    void substitution() {
        // canonical: G R OW TH  /  perceived: G R OW S  (gross)
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("G", "R", "OW", "TH"),
                List.of("G", "R", "OW", "S"));
        assertThat(ops).extracting(AlignmentOp::errorType).containsExactly(
                ErrorType.MATCH, ErrorType.MATCH, ErrorType.MATCH, ErrorType.SUBSTITUTION);
        AlignmentOp sub = ops.get(3);
        assertThat(sub.canonical()).isEqualTo("TH");
        assertThat(sub.perceived()).isEqualTo("S");
        assertThat(sub.canonicalIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("삭제: perceived 에 음소가 빠지면 DELETION")
    void deletion() {
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("L", "AH", "V"), List.of("L", "V"));
        assertThat(ops).extracting(AlignmentOp::errorType).containsExactly(
                ErrorType.MATCH, ErrorType.DELETION, ErrorType.MATCH);
        assertThat(ops.get(1).canonical()).isEqualTo("AH");
        assertThat(ops.get(1).canonicalIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("삽입: 여분 음소는 INSERTION, canonicalIndex = -1")
    void insertion() {
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("K", "AE", "T"),
                List.of("K", "AE", "T", "AH"));
        assertThat(ops).extracting(AlignmentOp::errorType).containsExactly(
                ErrorType.MATCH, ErrorType.MATCH, ErrorType.MATCH, ErrorType.INSERTION);
        assertThat(ops.get(3).perceived()).isEqualTo("AH");
        assertThat(ops.get(3).canonicalIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("대소문자 차이는 동일 음소로 본다")
    void caseInsensitive() {
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("HH", "EH"), List.of("hh", "eh"));
        assertThat(ops).allMatch(op -> op.errorType() == ErrorType.MATCH);
    }

    @Test
    @DisplayName("perceived 가 비면 전부 DELETION")
    void allDeletionWhenPerceivedEmpty() {
        List<AlignmentOp> ops = PhonemeAligner.align(List.of("AA", "B"), List.of());
        assertThat(ops).extracting(AlignmentOp::errorType).containsExactly(
                ErrorType.DELETION, ErrorType.DELETION);
    }
}
