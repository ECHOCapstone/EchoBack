package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;

// 모델 서버 호출의 단일 추상화. 구현체는 HTTP/gRPC/in-process 등 자유롭게 선택할 수 있다.
public interface ModelServerClient {

    ModelAnalyzeResponse analyze(byte[] audio, String filename, String contentType, String canonical);
}
