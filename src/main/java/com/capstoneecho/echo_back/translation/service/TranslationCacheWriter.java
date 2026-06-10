package com.capstoneecho.echo_back.translation.service;

import com.capstoneecho.echo_back.translation.entity.TranslationCache;
import com.capstoneecho.echo_back.translation.repository.TranslationCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 번역 캐시 INSERT 를 호출 측 트랜잭션과 분리한다.
// 캐시 저장은 학습 흐름(예: 세션 PATCH)의 부수 작업이라, 동시 요청이 같은 원문을 동시에 저장해 PK(원문 hash)
// 충돌이 나더라도 호출 측 트랜잭션까지 함께 롤백되면 안 된다. REQUIRES_NEW 로 독립 트랜잭션에서 즉시 flush 해,
// 충돌 시 이 트랜잭션만 롤백되고 예외는 호출 측이 잡아 무시할 수 있게 한다(베스트 에포트 캐시).
@Service
public class TranslationCacheWriter {

    private final TranslationCacheRepository repository;

    public TranslationCacheWriter(TranslationCacheRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(String sourceText, String targetText) {
        repository.saveAndFlush(TranslationCache.of(sourceText, targetText));
    }
}
