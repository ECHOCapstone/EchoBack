package com.capstoneecho.echo_back.pronunciation.phoneme.support;

// 음소 조음 이미지 바이트의 저장소. 한 음소당 하나의 이미지를 보관한다.
public interface PhonemeImageStorage {

    // 이미지를 저장하고 상대 경로를 돌려준다. 같은 음소·확장자면 덮어쓴다.
    String save(String phoneme, String extension, byte[] bytes);

    byte[] read(String imagePath);

    void delete(String imagePath);
}
