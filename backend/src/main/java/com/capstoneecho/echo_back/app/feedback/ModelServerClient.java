package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;

// 음성 분석을 모델 서버에 맡기는 호출 포트. 구현은 HTTP, gRPC, 인프로세스 등 자유롭게 갈 수 있다.
public interface ModelServerClient {

    ModelAnalyzeResponse analyze(byte[] audio, String filename, String contentType, String canonical);
}
