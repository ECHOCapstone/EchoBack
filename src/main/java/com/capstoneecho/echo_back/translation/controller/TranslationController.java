package com.capstoneecho.echo_back.translation.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.translation.dto.TranslationRequest;
import com.capstoneecho.echo_back.translation.dto.TranslationResponse;
import com.capstoneecho.echo_back.translation.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 영어 → 한국어 번역 진입점. 학습 화면이 챕터/세션 진입 시 prompt.target 묶음을 한 번 호출해
// 한국어 자막을 함께 받아간다. 인증된 사용자만 접근 가능 (SecurityConfig 의 anyRequest().authenticated()).
@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/batch")
    public ApiResponse<TranslationResponse> batch(@Valid @RequestBody TranslationRequest request) {
        return ApiResponse.success(translationService.translate(request.texts()));
    }
}
