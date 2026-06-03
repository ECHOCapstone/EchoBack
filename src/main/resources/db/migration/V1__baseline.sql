-- ECHO 베이스라인 스키마 (Flyway V1). Hibernate 매핑에서 MySQL 방언으로 추출한 현재 스키마.
-- 신규 변경은 V2 이후 마이그레이션으로 추가한다. (H2 dev/test 는 Hibernate 가 별도 관리)

create table demo_ranking_entries (accuracy integer not null, id bigint not null auto_increment, nickname varchar(30) not null, primary key (id)) engine=InnoDB;
create table learning_steps (id bigint not null auto_increment, script_id bigint not null, prompt TEXT not null, target_text TEXT, kind enum ('INTRO','RECORD') not null, primary key (id)) engine=InnoDB;
create table phoneme_errors (canonical_index integer, feedback_id bigint not null, id bigint not null auto_increment, canonical varchar(32), perceived varchar(32), op enum ('DEL','INS','SUB') not null, primary key (id)) engine=InnoDB;
-- pronunciation_feedbacks / recordings 의 XOR/배타 CHECK 는 MySQL 8.0.16+ 에서
-- ON DELETE SET NULL FK 와 동일 컬럼을 참조하면 error 3823 으로 거부된다 (referential action 충돌).
-- 같은 검증은 도메인 서비스(RecordingService / FeedbackService) 에서 이미 수행하므로
-- DB CHECK 는 생략한다.
create table pronunciation_feedbacks (accuracy float(53) not null, completed bit not null, completed_at datetime(6), created_at datetime(6) not null, id bigint not null auto_increment, script_id bigint, session_id bigint, user_id bigint not null, weak_phoneme varchar(32), practice_word varchar(100), task_title varchar(200), title varchar(200) not null, comprehensive_json TEXT, guidance_kr TEXT, script_text TEXT, primary key (id)) engine=InnoDB;
create table recordings (duration_sec float(53), step_score float(53), created_at datetime(6) not null, id bigint not null auto_increment, script_id bigint, session_id bigint, session_sentence_id bigint, step_id bigint, user_id bigint not null, audio_path varchar(500) not null, canonical TEXT, errors_json TEXT, guidance_kr TEXT, peak_softmax TEXT, perceived TEXT, target_text_snapshot TEXT, wrong_words_json TEXT, primary key (id)) engine=InnoDB;
create table retry_attempts (correct bit not null, score float(53), created_at datetime(6) not null, feedback_id bigint not null, id bigint not null auto_increment, word varchar(200) not null, canonical TEXT, errors_json TEXT, guidance_kr TEXT, perceived TEXT, primary key (id)) engine=InnoDB;
create table scripts (chapter_order integer, preset bit not null, id bigint not null auto_increment, track_id bigint, mastery_badge_name varchar(50), practice_word varchar(100), content TEXT not null, title varchar(255) not null, difficulty enum ('EASY','HARD','MEDIUM'), primary key (id)) engine=InnoDB;
create table session_sentences (sentence_index integer not null, id bigint not null auto_increment, session_id bigint not null, text TEXT not null, primary key (id)) engine=InnoDB;
create table sessions (favorite bit not null, created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, user_id bigint not null, title varchar(100) not null, script_text TEXT not null, primary key (id)) engine=InnoDB;
create table social_accounts (created_at datetime(6) not null, id bigint not null auto_increment, user_id bigint not null, access_token varchar(2048), provider_email varchar(255), provider_uid varchar(255) not null, provider enum ('GOOGLE','KAKAO') not null, primary key (id)) engine=InnoDB;
create table tracks (display_order integer not null, id bigint not null auto_increment, title varchar(100) not null, description TEXT, primary key (id)) engine=InnoDB;
create table users (exp integer not null, streak integer not null, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, id bigint not null auto_increment, last_study_at datetime(6), nickname varchar(30) not null, username varchar(50) not null, email varchar(100) not null, password_hash varchar(100), primary key (id)) engine=InnoDB;
create index idx_feedback_user_completed_at on pronunciation_feedbacks (user_id, completed_at);
create index ix_retry_attempts_feedback_created on retry_attempts (feedback_id, created_at);
create index ix_scripts_track on scripts (track_id, chapter_order);
create index ix_session_sentences_session on session_sentences (session_id, sentence_index);
alter table social_accounts add constraint uk_social_accounts_provider_uid unique (provider, provider_uid);
alter table users add constraint uk_users_username unique (username);
alter table users add constraint uk_users_email unique (email);
alter table learning_steps add constraint FK7tvqceql02allocwd1hl1r4nc foreign key (script_id) references scripts (id);
alter table phoneme_errors add constraint FKiiy3s5gli7lycf7loghylqyqw foreign key (feedback_id) references pronunciation_feedbacks (id);
alter table pronunciation_feedbacks add constraint FK6i6dspvoivjw5biuermpj4v76 foreign key (script_id) references scripts (id) on delete set null;
alter table pronunciation_feedbacks add constraint FKr6x40fj2502xdn0e8majekkkk foreign key (session_id) references sessions (id) on delete set null;
alter table pronunciation_feedbacks add constraint FK11u9jcmk3iscspr5oggsacwhs foreign key (user_id) references users (id);
alter table recordings add constraint FK5juijr01tborfr4ly2mh91be foreign key (script_id) references scripts (id) on delete set null;
alter table recordings add constraint FKfadxbpf0kklubedkjepunr2rh foreign key (session_id) references sessions (id) on delete set null;
alter table recordings add constraint FKtgekoetnnacg5spldg235w8j foreign key (session_sentence_id) references session_sentences (id) on delete set null;
alter table recordings add constraint FKpv1fobrl9cm27weftv1yw5abw foreign key (step_id) references learning_steps (id) on delete set null;
alter table recordings add constraint FKlr4t7g9f4yrn32vc6it2amwac foreign key (user_id) references users (id);
alter table retry_attempts add constraint FKri7u11tjcunrelg5v896nwfln foreign key (feedback_id) references pronunciation_feedbacks (id);
alter table scripts add constraint FKbbkbrhiyyjvr4xv4lcu0gfn5x foreign key (track_id) references tracks (id);
alter table session_sentences add constraint FKlkoxdynpog0ku8e1xs263rwcv foreign key (session_id) references sessions (id);
alter table sessions add constraint FKruie73rneumyyd1bgo6qw8vjt foreign key (user_id) references users (id);
alter table social_accounts add constraint FK6rmxxiton5yuvu7ph2hcq2xn7 foreign key (user_id) references users (id);
