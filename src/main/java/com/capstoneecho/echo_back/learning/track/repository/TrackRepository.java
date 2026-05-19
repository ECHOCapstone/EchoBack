package com.capstoneecho.echo_back.learning.track.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.capstoneecho.echo_back.learning.track.entity.Track;
public interface TrackRepository extends JpaRepository<Track, Long> {

    // displayOrder 는 시드 단계에서 유일하게 부여되므로 추가 정렬키 없이도 결정적이다.
    List<Track> findAllByOrderByDisplayOrderAsc();
}
