package com.capstoneecho.echo_back.global.common;

import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import org.springframework.beans.factory.ObjectProvider;
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
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST, uploadTooLargeMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, null));
    }

    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + ": " + (message == null ? "invalid" : message);
    }
}
