package com.capstoneecho.echo_back.global.common;

import org.springframework.http.HttpStatus;

// 이 백엔드가 반환하는 모든 에러의 단일 카탈로그.
// 새 에러 케이스는 여기에만 추가하면 응답·메시지·HTTP 상태가 한 곳에서 관리된다.
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "필수 값이 누락되었거나 형식이 올바르지 않습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),

    USERNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 트랙을 찾을 수 없습니다."),
    SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다."),
    STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "학습 단계를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "세션의 학습 문장을 찾을 수 없습니다."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹음을 찾을 수 없습니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다."),

    AUDIO_DECODE_FAILED(HttpStatus.BAD_REQUEST, "오디오 파일을 처리할 수 없습니다."),
    MODEL_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "모델 서버에 연결할 수 없습니다."),
    MODEL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "모델 서버 처리 중 오류가 발생했습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예기치 않은 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    // Spring 의 ResponseEntity.status(int) 와 MockMvc 의 status().is(int) 가 같이 받을 수 있게
    // status code 를 정수로 노출한다. HttpStatus 객체가 필요한 호출 경로는 httpStatus() 로 별도 제공.
    public int getStatusCode() {
        return status.value();
    }

    public HttpStatus httpStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
