package com.capstoneecho.echo_back.learning.track.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.capstoneecho.echo_back.learning.track.entity.Track;
public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findAllByOrderByDisplayOrderAscIdAsc();
}
