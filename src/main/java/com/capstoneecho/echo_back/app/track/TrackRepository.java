package com.capstoneecho.echo_back.app.track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findAllByOrderByDisplayOrderAscIdAsc();
}
