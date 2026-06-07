-- 시드 시점에 의문문임에도 물음표가 빠져 있던 두 학습 단계를 표준 의문문 표기로 교정한다.
--
-- 트랙 시드는 tracks.yaml 에서 DB 가 빈 경우에만 한 번 복사되므로, yaml 수정만으로는 운영 DB 의
-- 이미 박힌 두 row 가 갱신되지 않는다. 이 마이그레이션이 운영 DB 의 해당 row 만 정확히 교정한다.
--
-- 다른 사용자 학습 데이터(recordings / pronunciation_feedbacks / progress) 는 step_id 외래키로
-- 연결되어 있고 본문 텍스트만 바꾸므로 진행도와 무관하게 안전하다.
UPDATE learning_steps
   SET target_text = 'Excuse me, is this seat available?',
       prompt = REPLACE(prompt, 'available 를 발음', 'available? 를 발음')
 WHERE target_text = 'Excuse me, is this seat available';

UPDATE learning_steps
   SET target_text = 'So what brings you to the event tonight?',
       prompt = REPLACE(prompt, 'tonight 를 발음', 'tonight? 를 발음')
 WHERE target_text = 'So what brings you to the event tonight';
