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

    public record ImageData(byte[] bytes, String contentType) {}
}
