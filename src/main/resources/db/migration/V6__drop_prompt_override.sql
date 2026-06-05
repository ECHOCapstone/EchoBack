-- prompt_override 테이블 제거. 프롬프트 편집본은 PromptCatalog 가 파일(app.content.dir/prompts/*.md)로
-- 저장하며 이 테이블을 읽거나 쓰는 코드가 없다. 비어 있는 채로 방치된 죽은 스키마를 정리한다.
-- (Flyway 가 ${...} 를 placeholder 로 해석하지 않게 주석에서도 $ 를 제거했다.)
DROP TABLE IF EXISTS prompt_override;
