-- 영어 → 한국어 번역 결과 캐시. 같은 영어 입력이 다시 들어왔을 때 외부 LLM 호출 없이 즉시 응답한다.
-- 키는 SHA-256 hex(64) 로 잡아 본문 길이와 무관하게 일정한 PK 크기를 유지한다.
CREATE TABLE translation_cache (
    text_hash   CHAR(64)  NOT NULL,
    source_text TEXT      NOT NULL,
    target_text TEXT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (text_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
