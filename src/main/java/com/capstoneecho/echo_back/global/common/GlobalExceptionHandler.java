package com.capstoneecho.echo_back.global.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// 이 백엔드의 예외 → ApiResponse 변환 단일 지점. ErrorCode 가 정의된 예외는
// 그 코드의 HTTP 상태로, 그 외는 INTERNAL_ERROR 로 정형화된다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity
                .status(e.code().status())
                .body(ApiResponse.fail(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        var msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, msg));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST, "업로드 파일 크기가 제한을 초과했습니다."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST, e.getMessage()));
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuth(Exception e) {
        return ResponseEntity
                .status(ErrorCode.UNAUTHORIZED.status())
                .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, e.getMessage()));
    }
}
