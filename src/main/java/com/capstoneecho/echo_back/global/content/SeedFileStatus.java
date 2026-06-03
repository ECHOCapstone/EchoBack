package com.capstoneecho.echo_back.global.content;

import java.time.Instant;

// PersistableSeed 도메인의 외부 영구 저장본이 현재 어떤 상태인지 UI 에 노출하기 위한 묶음.
//   exists      : 영구 저장본이 디스크에 존재하는지 (없으면 공장 기본값으로 동작 중)
//   lastModified: 영구 저장본의 마지막 수정 시각 (없으면 null)
//   path        : 운영자가 직접 git 에 반영하고 싶을 때 참조할 절대/상대 경로
public record SeedFileStatus(
        boolean exists,
        Instant lastModified,
        String path
) {}
