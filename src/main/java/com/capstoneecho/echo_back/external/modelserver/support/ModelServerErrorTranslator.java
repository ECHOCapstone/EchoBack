package com.capstoneecho.echo_back.external.modelserver.support;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

// 모델 서버 호출 실패를 "경계에서 먼저 로깅하고, 매핑된 BusinessException 을 반환" 하는 단일 출처.
// 클라이언트는 throw translator.xxx(...) 형태로 반환값을 던지므로, ControllerAdvice 로 전파되기 전에
// 모델 서버 고유 컨텍스트(엔드포인트, upstream 예외, upstream status/body)가 항상 먼저 기록된다.
// advice 의 요청 컨텍스트 로그는 upstream 원인을 잃으므로(새 BusinessException 만 전파됨) 여기서 보완한다.
@Component
public class ModelServerErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(ModelServerErrorTranslator.class);

    // body 가 길면 로그가 오염되므로 절단한다. RequestContextLogFormatter.safeQuery 의 256 절단과 같은 사상.
    private static final int MAX_DETAIL_LENGTH = 512;

    private static final String LOG_FORMAT =
            "모델 서버 호출 실패: endpoint={}, code={}, upstreamEx={}, status={}, detail={}";

    // 연결 거부 / 타임아웃 등 네트워크 단계 실패. 원인 추적을 위해 upstream 예외를 스택트레이스와 함께 남긴다.
    public BusinessException unavailable(String endpoint, ResourceAccessException ex) {
        log.error(LOG_FORMAT, endpoint, ErrorCode.MODEL_SERVER_UNAVAILABLE.name(),
                ex.getClass().getSimpleName(), "-", ex.getMessage(), ex);
        return new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, ex.getMessage());
    }

    // 모델 서버가 4xx/5xx 응답을 돌려준 경우. upstream status 와 응답 본문(절단)을 함께 남긴다.
    public BusinessException responseError(String endpoint, RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        log.error(LOG_FORMAT, endpoint, ErrorCode.MODEL_SERVER_ERROR.name(),
                ex.getClass().getSimpleName(), ex.getStatusCode().value(), truncate(body), ex);
        return new BusinessException(ErrorCode.MODEL_SERVER_ERROR, body);
    }

    // HTTP 자체는 성공(200)했지만 본문이 비었거나 필수 필드가 없는 경우. upstream 예외가 없어 스택트레이스도 없다.
    public BusinessException emptyResponse(String endpoint, String detail) {
        log.error(LOG_FORMAT, endpoint, ErrorCode.MODEL_SERVER_ERROR.name(), "-", "-", detail);
        return new BusinessException(ErrorCode.MODEL_SERVER_ERROR, detail);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= MAX_DETAIL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
