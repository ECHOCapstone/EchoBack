package com.capstoneecho.echo_back.pronunciation.phoneme.repository;

import com.capstoneecho.echo_back.pronunciation.phoneme.entity.PhonemeAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhonemeAssetRepository extends JpaRepository<PhonemeAsset, String> {
}
