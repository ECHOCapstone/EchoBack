-- 런타임 편집 가능한 설정 오버라이드. 키마다 yaml 기본값을 덮어쓰는 값을 보관한다.
CREATE TABLE app_setting (
    setting_key   VARCHAR(100) NOT NULL,
    setting_value TEXT,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB;
