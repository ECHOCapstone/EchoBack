package com.capstoneecho.echo_back.learning.progress.dto;

import jakarta.validation.constraints.Min;

// step / sentence 완료를 보고하는 요청. 0-based 인덱스. 음수는 허용하지 않는다 (아직 어떤 것도 안 했으면 호출 자체를 하지 않는다).
public record AdvanceProgressRequest(@Min(0) int index) {}
