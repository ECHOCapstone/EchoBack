package com.capstoneecho.echo_back.app.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<Script, Long> {

    // 트랙에 묶이지 않은 사전 정의 스크립트 (legacy 호환). 트랙 도입 후로는 거의 사용되지 않는다.
    // primitive boolean preset 의 JPA 프로퍼티 이름은 'preset' 이라 메서드 이름도 By-Preset 으로 표기한다.
    List<Script> findByPresetTrueOrderByIdAsc();

    List<Script> findByTrack_IdOrderByChapterOrderAscIdAsc(Long trackId);
}
