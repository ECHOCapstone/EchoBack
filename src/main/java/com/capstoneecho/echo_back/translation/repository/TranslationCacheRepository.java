package com.capstoneecho.echo_back.translation.repository;

import com.capstoneecho.echo_back.translation.entity.TranslationCache;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 번역 캐시 저장소. 여러 원문의 hash 를 한 번에 받아 캐시 hit 여부를 일괄 조회한다.
public interface TranslationCacheRepository extends JpaRepository<TranslationCache, String> {

    List<TranslationCache> findAllByTextHashIn(Collection<String> hashes);
}
