-- demo_ranking_entries 테이블 제거. 더미 시드 사용자 없이 실제 학습 활동만 랭킹에 반영하는
-- 정책으로 전환하면서 더 이상 쓰이지 않는 스키마를 정리한다.
DROP TABLE IF EXISTS demo_ranking_entries;
