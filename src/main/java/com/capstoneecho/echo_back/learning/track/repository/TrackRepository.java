package com.capstoneecho.echo_back.learning.track.repository;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findAllByOrderByDisplayOrderAsc();
}
