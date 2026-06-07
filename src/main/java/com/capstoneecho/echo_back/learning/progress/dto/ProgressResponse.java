package com.capstoneecho.echo_back.learning.progress.dto;

// 학습 단위(챕터 / 세션) 의 현재 진행 상태.
//   exists                 : DB 에 진행 상태 행이 존재하는지. false 면 처음 진입한 단위.
//   lastCompletedIndex     : 마지막으로 완료한 step / sentence 의 0-based 인덱스. -1 이면 시작만 한 상태.
//                            FE 는 lastCompletedIndex + 1 부터 학습을 재개한다.
public record ProgressResponse(boolean exists, int lastCompletedIndex) {

    public static ProgressResponse empty() {
        return new ProgressResponse(false, -1);
    }

    public static ProgressResponse of(int lastCompletedIndex) {
        return new ProgressResponse(true, lastCompletedIndex);
    }
}
