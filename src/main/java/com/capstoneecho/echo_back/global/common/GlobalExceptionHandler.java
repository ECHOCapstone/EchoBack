package com.capstoneecho.echo_back.global.common;

import com.capstoneecho.echo_back.global.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// REST 컨트롤러 예외 → ApiResponse 봉투 변환 단일 출처.
// 도메인 예외 (BusinessException) 는 ErrorCode 의 상태 / 메시지를 사용하고,
// 검증 / 파일 크기 / 미예측 예외는 ErrorCode 의 기본 메시지나 app.messages 폴백을 쓴다.
// AppProperties 가 컨텍스트에 없는 슬라이스 테스트에서도 동작하도록 ObjectProvider 로 받는다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String uploadTooLargeMessage;

    public GlobalExceptionHandler(ObjectProvider<AppProperties> appPropertiesProvider) {
        AppProperties appProperties = appPropertiesProvider.getIfAvailable();
        AppProperties.Messages m = appProperties == null ? null : appProperties.messages();
        this.uploadTooLargeMessage = (m == null || m.uploadTooLarge() == null)
                ? ErrorCode.INVALID_REQUEST.getDefaultMessage()
                : m.uploadTooLarge();
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
                .body(ApiResponse.failure(ErrorCode.INVALID_REQUEST, uploadTooLargeMessage));
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
