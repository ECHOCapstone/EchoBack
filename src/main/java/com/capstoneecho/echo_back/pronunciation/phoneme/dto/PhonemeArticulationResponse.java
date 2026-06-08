package com.capstoneecho.echo_back.pronunciation.phoneme.dto;

// 음소 한 개의 조음 안내. 한글 음차·설명·이미지 경로를 함께 내려 프론트 조음 카드의 단일 출처가 된다.
// imageUrl 은 이미지 바이트를 서빙하는 공개 엔드포인트 경로다.
public record PhonemeArticulationResponse(
        String phoneme, String koreanCue, String tip, String imageUrl) {

    public static String imageUrl(String phoneme) {
        return "/api/phonemes/" + phoneme + "/image";
    }
}
