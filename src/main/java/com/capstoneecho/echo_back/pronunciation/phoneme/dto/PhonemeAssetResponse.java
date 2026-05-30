package com.capstoneecho.echo_back.pronunciation.phoneme.dto;

import com.capstoneecho.echo_back.pronunciation.phoneme.entity.PhonemeAsset;

// 음소 이미지 한 건. imageUrl 은 이미지 바이트를 서빙하는 공개 엔드포인트 경로.
public record PhonemeAssetResponse(String phoneme, String imageUrl) {

    public static PhonemeAssetResponse from(PhonemeAsset asset) {
        return new PhonemeAssetResponse(asset.getPhoneme(), imageUrl(asset.getPhoneme()));
    }

    public static String imageUrl(String phoneme) {
        return "/api/phonemes/" + phoneme + "/image";
    }
}
