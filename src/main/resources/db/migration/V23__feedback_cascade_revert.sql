-- 학습 진행 / 콘텐츠 정리 후에도 학습자의 발음 기록을 보존하도록 SET NULL 로 복귀.
-- V17 가 pronunciation_feedbacks / recordings 의 콘텐츠 FK (script_id / session_id /
-- session_sentence_id / step_id) 를 ON DELETE CASCADE 로 바꿔, 부모 콘텐츠를 지우면
-- 학습 기록이 함께 사라지는 데이터 손실이 발생했다. 부모가 사라져도 학습자의 기록과
-- denormalized 스냅샷 (title / target_text_snapshot 등) 은 남도록 SET NULL 로 되돌린다.
-- user_id 의 CASCADE 는 그대로 둔다 — retention 스케줄러의 자동 정리 기반이다.
-- (NULL/NULL 분기는 FeedbackService.complete 에서 명시 처리.)

-- ---------- pronunciation_feedbacks ----------
ALTER TABLE pronunciation_feedbacks
    DROP FOREIGN KEY fk_pf_script;
ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT fk_pf_script
        FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE SET NULL;

ALTER TABLE pronunciation_feedbacks
    DROP FOREIGN KEY fk_pf_session;
ALTER TABLE pronunciation_feedbacks
    ADD CONSTRAINT fk_pf_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL;

-- ---------- recordings ----------
ALTER TABLE recordings
    DROP FOREIGN KEY fk_rec_script;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_script
        FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE SET NULL;

ALTER TABLE recordings
    DROP FOREIGN KEY fk_rec_session;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_session
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL;

ALTER TABLE recordings
    DROP FOREIGN KEY fk_rec_sentence;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_sentence
        FOREIGN KEY (session_sentence_id) REFERENCES session_sentences(id) ON DELETE SET NULL;

ALTER TABLE recordings
    DROP FOREIGN KEY fk_rec_step;
ALTER TABLE recordings
    ADD CONSTRAINT fk_rec_step
        FOREIGN KEY (step_id) REFERENCES learning_steps(id) ON DELETE SET NULL;
