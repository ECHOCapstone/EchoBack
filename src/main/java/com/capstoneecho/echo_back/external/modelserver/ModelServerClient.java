package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;

// 모델 서버에 위임하는 호출 포트. 구현은 HTTP, gRPC, 인프로세스 등 자유롭게 갈 수 있다.
public interface ModelServerClient {

    // 오디오 + (선택)정답 음소 시퀀스를 보내 perceived/alignment/errors/per 을 받는다.
    AnalyzeResult analyze(byte[] audio, String filename, String contentType, String canonical);

    // 영문 텍스트를 모델 인벤토리에 맞춘 ARPAbet 음소 시퀀스(공백 구분 문자열) 로 변환한다.
    // 변환 결과가 비어 있을 수 있으므로 호출자는 빈 문자열 케이스를 안전히 다뤄야 한다.
    String g2p(String text);
}
