-- 프롬프트 본문 오버라이드. classpath 의 기본 프롬프트(.md)를 런타임에 덮어쓴다.
-- 키는 프롬프트 파일명(system / step-feedback / ...). 행이 없으면 기본값을 쓴다.
CREATE TABLE prompt_override (
    prompt_key VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (prompt_key)
) ENGINE=InnoDB;
