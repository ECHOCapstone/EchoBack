package com.capstoneecho.echo_back.translation.service;

import com.capstoneecho.echo_back.external.llm.translation.TranslationClient;
import com.capstoneecho.echo_back.translation.dto.TranslationResponse;
import com.capstoneecho.echo_back.translation.entity.TranslationCache;
import com.capstoneecho.echo_back.translation.repository.TranslationCacheRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 번역 도메인 진입점. 입력 원문 묶음을 받아 캐시 hit / miss 를 가르고, miss 만 외부 client 로 한 번에 보낸다.
// 같은 원문이 같은 요청에 중복 등장해도 hash 정규화 덕에 외부 호출은 한 번만 일어난다.
//
// 트랜잭션 정책: 읽기 (캐시 조회) 와 쓰기 (miss 저장) 가 분리된 두 단계라 메서드 단위로 readOnly=false 를 둔다.
// 저장 실패는 응답을 막지 않아야 하므로 try / catch 로 격리해 캐시 갱신을 베스트 에포트로 다룬다.
@Service
@Transactional
public class TranslationService {

    private final TranslationCacheRepository repository;
    private final TranslationClient client;

    public TranslationService(TranslationCacheRepository repository, TranslationClient client) {
        this.repository = repository;
        this.client = client;
    }

    public TranslationResponse translate(List<String> rawTexts) {
        if (rawTexts == null || rawTexts.isEmpty()) {
            return new TranslationResponse(List.of());
        }

        // 입력 원문을 다듬어 정규화한다. 같은 문장이 여러 번 들어와도 외부 호출은 한 번만 발생하도록
        // hash 키로 묶고 결과를 입력 순서대로 다시 펼친다.
        List<String> normalized = normalize(rawTexts);
        Map<String, String> resolvedByHash = resolveFromCache(normalized);
        List<String> missingHashes = collectMissing(normalized, resolvedByHash);

        if (!missingHashes.isEmpty()) {
            translateMissingAndStore(normalized, resolvedByHash, missingHashes);
        }

        return assemble(normalized, resolvedByHash);
    }

    // 공백 다듬기. 빈 문자열도 입력 순서를 유지하기 위해 그대로 둔다 (빈 원문은 빈 번역으로 응답).
    private List<String> normalize(List<String> raw) {
        List<String> result = new ArrayList<>(raw.size());
        for (String text : raw) {
            result.add(text == null ? "" : text.trim());
        }
        return result;
    }

    // 캐시에서 hash 별 번역을 끌어온다. 빈 원문은 조회 자체를 건너뛰고 hash 매핑에 빈 문자열을 남긴다.
    private Map<String, String> resolveFromCache(List<String> normalized) {
        Map<String, String> resolved = new HashMap<>();
        List<String> hashesToQuery = new ArrayList<>();
        for (String text : normalized) {
            if (text.isEmpty()) continue;
            String hash = TranslationCache.hashOf(text);
            if (!resolved.containsKey(hash)) {
                hashesToQuery.add(hash);
            }
        }
        if (hashesToQuery.isEmpty()) {
            return resolved;
        }
        for (TranslationCache cache : repository.findAllByTextHashIn(hashesToQuery)) {
            resolved.put(cache.getTextHash(), cache.getTargetText());
        }
        return resolved;
    }

    // 캐시 hit 가 아닌 hash 들을 입력 순서대로 모은다. 같은 hash 는 한 번만 모은다.
    private List<String> collectMissing(List<String> normalized, Map<String, String> resolved) {
        Map<String, String> missingTextByHash = new LinkedHashMap<>();
        for (String text : normalized) {
            if (text.isEmpty()) continue;
            String hash = TranslationCache.hashOf(text);
            if (resolved.containsKey(hash)) continue;
            missingTextByHash.putIfAbsent(hash, text);
        }
        return List.copyOf(missingTextByHash.keySet());
    }

    // miss 들을 외부 client 에 한 번에 보내고, 빈 결과가 아닌 항목만 캐시에 저장한다.
    private void translateMissingAndStore(
            List<String> normalized, Map<String, String> resolvedByHash, List<String> missingHashes) {
        // hash → text 매핑을 다시 만들어 외부 client 에는 원문 리스트만 넘긴다 (hash 노출 X).
        Map<String, String> textByHash = new LinkedHashMap<>();
        for (String text : normalized) {
            if (text.isEmpty()) continue;
            textByHash.putIfAbsent(TranslationCache.hashOf(text), text);
        }
        List<String> missingTexts = new ArrayList<>(missingHashes.size());
        for (String hash : missingHashes) {
            missingTexts.add(textByHash.get(hash));
        }

        List<String> translations = client.translate(missingTexts);
        for (int i = 0; i < missingHashes.size(); i++) {
            String hash = missingHashes.get(i);
            String translated = i < translations.size() ? translations.get(i) : "";
            String safe = translated == null ? "" : translated.trim();
            resolvedByHash.put(hash, safe);
            // 빈 결과 (외부 호출 실패 또는 Noop 폴백) 는 캐시에 남기지 않는다 — 다음 요청에서 다시 시도할 수 있게.
            if (safe.isEmpty()) continue;
            persistQuietly(textByHash.get(hash), safe);
        }
    }

    // 캐시 저장 실패가 사용자 응답을 막지 않도록 격리한다. 중복 저장은 hash PK 충돌로 자연스럽게 막힌다.
    private void persistQuietly(String sourceText, String targetText) {
        try {
            repository.save(TranslationCache.of(sourceText, targetText));
        } catch (RuntimeException ignored) {
            // 동시 요청으로 같은 hash 가 PK 충돌을 일으켜도 응답에는 영향 없다.
        }
    }

    // 입력 순서대로 응답을 조립한다. 원문이 비어 있거나 번역이 없으면 빈 문자열로 채워 길이를 맞춘다.
    private TranslationResponse assemble(List<String> normalized, Map<String, String> resolvedByHash) {
        List<TranslationResponse.Item> items = new ArrayList<>(normalized.size());
        for (String text : normalized) {
            String target = text.isEmpty() ? "" : resolvedByHash.getOrDefault(TranslationCache.hashOf(text), "");
            items.add(new TranslationResponse.Item(text, target));
        }
        return new TranslationResponse(items);
    }
}
