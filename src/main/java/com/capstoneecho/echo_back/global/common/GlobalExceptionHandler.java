package com.capstoneecho.echo_back.global.common;

import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// REST 컨트롤러 예외 → ApiResponse 봉투 변환 단일 출처.
// 도메인 예외 (BusinessException) 는 ErrorCode 의 상태 / 메시지를 사용하고,
// 검증 / 파일 크기 / 미예측 예외는 ErrorCode 의 기본 메시지나 메시지 설정 폴백을 쓴다.
// RuntimeSettings 가 컨텍스트에 없는 슬라이스 테스트에서도 동작하도록 ObjectProvider 로 받는다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectProvider<RuntimeSettings> settingsProvider;

    public GlobalExceptionHandler(ObjectProvider<RuntimeSettings> settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    // 업로드 초과 안내 문구. 설정이 없으면(슬라이스 테스트) ErrorCode 기본 메시지로 폴백한다.
    private String uploadTooLargeMessage() {
        RuntimeSettings settings = settingsProvider.getIfAvailable();
        if (settings == null) {
            return ErrorCode.INVALID_REQUEST.getDefaultMessage();
        }
        String message = settings.uploadTooLarge();
        return (message == null || message.isBlank())
                ? ErrorCode.INVALID_REQUEST.getDefaultMessage()
                : message;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode code = ex.getCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.failure(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.VALIDATION_FAILED.getDefaultMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // 업로드 크기 초과는 의미상 413 Payload Too Large. 본문 코드는 클라이언트 메시지 매핑을 위해 유지한다.
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST, uploadTooLargeMessage()));
    }

    // DB 제약(unique / FK / NOT NULL) 위반은 의미상 클라이언트 요청이 충돌 / 중복인 경우가 많아
    // 500 INTERNAL_ERROR 가 아닌 409 CONFLICT 로 전달한다. 본문 코드는 INVALID_REQUEST 를 재사용해
    // FE 의 에러 매핑 사전에 새 코드 추가 없이 처리되도록 한다.
    // 특정 제약 (활성 챌린지 단일성 등) 은 원인이 명확한 경우 사용자용 안내 메시지를 보강한다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String causeMsg = ex.getMostSpecificCause().toString();
        log.warn("Data integrity violation: {}", causeMsg);
        String userMessage = resolveDataIntegrityMessage(causeMsg);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST, userMessage));
    }

    // 제약 이름이 cause 메시지에 노출되는 MySQL/H2 의 동작에 의지해 원인을 분기한다.
    // 어떤 제약인지 식별되지 않으면 기본 충돌 안내로 떨어진다.
    private String resolveDataIntegrityMessage(String causeMessage) {
        if (causeMessage == null) {
            return "동일한 정보로 이미 등록된 항목이 있어 처리할 수 없습니다.";
        }
        String lower = causeMessage.toLowerCase();
        if (lower.contains("uk_daily_challenges_active_marker")) {
            return "이미 활성화된 챌린지가 있습니다. 기존 챌린지를 먼저 종료한 뒤 다시 시도해 주세요.";
        }
        if (lower.contains("uk_users_username")) {
            return "이미 사용 중인 사용자명입니다.";
        }
        if (lower.contains("uk_users_email")) {
            return "이미 사용 중인 이메일입니다.";
        }
        return "동일한 정보로 이미 등록된 항목이 있어 처리할 수 없습니다.";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        // 운영에서 원인 추적이 가능하도록 stacktrace 를 함께 남긴다 — 그 외 사용자 응답은 단일 메시지.
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, null));
    }

    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + ": " + (message == null ? "invalid" : message);
    }
}
