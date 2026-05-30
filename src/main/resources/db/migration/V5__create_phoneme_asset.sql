-- 음소별 조음 이미지. phoneme 은 stress 숫자 없는 대문자 ARPAbet (R, AY, TH ...).
-- 실제 이미지 파일은 스토리지에 두고, image_path 로 가리킨다.
CREATE TABLE phoneme_asset (
    phoneme      VARCHAR(8)   NOT NULL,
    image_path   VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (phoneme)
) ENGINE=InnoDB;
