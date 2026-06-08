-- 음소 조음 이미지를 DB+스토리지 관리에서 번들 classpath 리소스(content/articulation/)로 전환.
-- 매핑은 phoneme-inventory.json(SSOT) 이 대신하므로 더 이상 이 테이블이 필요 없다.
DROP TABLE IF EXISTS phoneme_asset;
