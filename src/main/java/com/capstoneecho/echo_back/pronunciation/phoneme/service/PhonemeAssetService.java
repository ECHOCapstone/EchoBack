package com.capstoneecho.echo_back.pronunciation.phoneme.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.pronunciation.phoneme.dto.PhonemeAssetResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.entity.PhonemeAsset;
import com.capstoneecho.echo_back.pronunciation.phoneme.repository.PhonemeAssetRepository;
import com.capstoneecho.echo_back.pronunciation.phoneme.support.PhonemeImageStorage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 음소 조음 이미지의 업로드 / 목록 / 서빙 / 삭제. 이미지 바이트는 스토리지, 매핑은 DB 에 둔다.
@Service
@Transactional
public class PhonemeAssetService {

    // 지원하는 이미지 형식 → 저장 확장자.
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");
    // stress 숫자 없는 대문자 ARPAbet (R, AY, TH ...).
    private static final Pattern PHONEME = Pattern.compile("^[A-Z]{1,4}$");

    private final PhonemeAssetRepository repository;
    private final PhonemeImageStorage storage;

    public PhonemeAssetService(PhonemeAssetRepository repository, PhonemeImageStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<PhonemeAssetResponse> list() {
        return repository.findAll(Sort.by("phoneme")).stream()
                .map(PhonemeAssetResponse::from)
                .toList();
    }

    public PhonemeAssetResponse upload(String rawPhoneme, byte[] bytes, String contentType) {
        String phoneme = normalize(rawPhoneme);
        String extension = EXTENSION_BY_CONTENT_TYPE.get(
                contentType == null ? "" : contentType.toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "지원하지 않는 이미지 형식입니다: " + contentType);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미지가 비어 있습니다.");
        }
        // Content-Type 헤더는 클라이언트가 임의로 바꿀 수 있으므로 파일 시그니처(magic number)로 한 번 더 검증한다.
        // 실제 바이트가 다른 형식이면 (예: image/png 헤더 + .exe 본문) 거절해 비정상 자산 업로드를 막는다.
        if (!matchesExtension(extension, bytes)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "이미지 형식과 실제 파일 내용이 일치하지 않습니다.");
        }
        String newPath = storage.save(phoneme, extension, bytes);
        repository.findById(phoneme).ifPresentOrElse(
                existing -> {
                    // 확장자가 바뀌어 경로가 달라지면 이전 파일을 정리한다.
                    if (!existing.getImagePath().equals(newPath)) {
                        storage.delete(existing.getImagePath());
                    }
                    existing.update(newPath, contentType);
                },
                () -> repository.save(PhonemeAsset.of(phoneme, newPath, contentType)));
        return new PhonemeAssetResponse(phoneme, PhonemeAssetResponse.imageUrl(phoneme));
    }

    @Transactional(readOnly = true)
    public ImageData image(String rawPhoneme) {
        PhonemeAsset asset = repository.findById(normalize(rawPhoneme))
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONEME_ASSET_NOT_FOUND));
        return new ImageData(storage.read(asset.getImagePath()), asset.getContentType());
    }

    public void delete(String rawPhoneme) {
        PhonemeAsset asset = repository.findById(normalize(rawPhoneme))
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONEME_ASSET_NOT_FOUND));
        storage.delete(asset.getImagePath());
        repository.delete(asset);
    }

    private String normalize(String rawPhoneme) {
        String phoneme = rawPhoneme == null ? "" : rawPhoneme.trim().toUpperCase(Locale.ROOT);
        if (!PHONEME.matcher(phoneme).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "잘못된 음소입니다: " + rawPhoneme);
        }
        return phoneme;
    }

    // 업로드 바이트의 시그니처가 클라이언트가 알려준 확장자와 일치하는지 검사한다.
    //   png   : 89 50 4E 47 0D 0A 1A 0A
    //   jpg   : FF D8 FF
    //   webp  : RIFF .... WEBP (12 bytes)
    //   svg   : "<svg" 또는 BOM 후 "<svg" 또는 "<?xml" 헤더 (XML)
    private static boolean matchesExtension(String extension, byte[] bytes) {
        return switch (extension) {
            case "png" -> startsWith(bytes,
                    (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A);
            case "jpg" -> startsWith(bytes, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
            case "webp" -> bytes.length >= 12
                    && startsWith(bytes, (byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F')
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "svg" -> looksLikeSvg(bytes);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    // svg 는 XML 텍스트라 앞부분에 "<svg" 또는 XML 선언이 나타난다.
    private static boolean looksLikeSvg(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 512), java.nio.charset.StandardCharsets.UTF_8);
        String lowered = head.toLowerCase(Locale.ROOT).strip();
        // UTF-8 BOM 제거.
        if (lowered.startsWith("﻿")) {
            lowered = lowered.substring(1).strip();
        }
        return lowered.startsWith("<svg")
                || (lowered.startsWith("<?xml") && lowered.contains("<svg"));
    }

    public record ImageData(byte[] bytes, String contentType) {}
}
