package com.capstoneecho.echo_back.learning.script.entity;

// 학습 단계의 종류.
//   INTRO  - 다음 녹음 묶음에 대한 안내 메시지. 녹음 없음.
//   RECORD - 사용자가 발음하고 녹음을 업로드해야 하는 단계.
public enum StepKind {
    INTRO,
    RECORD
}
