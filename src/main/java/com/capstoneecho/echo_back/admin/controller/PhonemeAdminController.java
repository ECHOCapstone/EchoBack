package com.capstoneecho.echo_back.admin.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.dto.PhonemeAssetResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.service.PhonemeAssetService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 음소 조음 이미지 관리 (업로드/목록/삭제). SecurityConfig 가 /api/admin/** 를 ROLE_ADMIN 으로 보호한다.
@RestController
@RequestMapping("/api/admin/phonemes")
public class PhonemeAdminController {

    private final PhonemeAssetService phonemeAssetService;

    public PhonemeAdminController(PhonemeAssetService phonemeAssetService) {
        this.phonemeAssetService = phonemeAssetService;
    }

    @GetMapping
    public ApiResponse<List<PhonemeAssetResponse>> list() {
        return ApiResponse.success(phonemeAssetService.list());
    }

    @PostMapping(value = "/{phoneme}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhonemeAssetResponse> upload(
            @PathVariable String phoneme, @RequestParam("image") MultipartFile image) {
        return ApiResponse.success(
                phonemeAssetService.upload(phoneme, readBytes(image), image.getContentType()));
    }

    @DeleteMapping("/{phoneme}")
    public ApiResponse<Void> delete(@PathVariable String phoneme) {
        phonemeAssetService.delete(phoneme);
        return ApiResponse.success(null);
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read uploaded image", e);
        }
    }
}
