package com.capstoneecho.echo_back.system;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

// 인증이 필요 없는 부팅 확인용 엔드포인트.
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "echo-app-backend",
                "timestamp", Instant.now().toString()
        ));
    }
}
