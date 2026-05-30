package com.capstoneecho.echo_back.global.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    AUDIO_DECODE_FAILED(HttpStatus.BAD_REQUEST, "오디오 디코딩에 실패했습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "트랙을 찾을 수 없습니다."),
    SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다."),
    STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "스크립트 단계를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_SENTENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "세션 문장을 찾을 수 없습니다."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹음을 찾을 수 없습니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "피드백을 찾을 수 없습니다."),

    USERNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 사용자명입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    TRACK_HAS_SCRIPTS(HttpStatus.CONFLICT, "스크립트가 연결된 트랙은 삭제할 수 없습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    MODEL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "모델 서버에서 오류가 발생했습니다."),
    MODEL_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "모델 서버가 응답하지 않습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getStatusCode() {
        return status.value();
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
