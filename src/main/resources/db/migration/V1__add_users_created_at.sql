-- Adds users.created_at to satisfy FRONT_API_SPEC §12 (User.createdAt).
-- Existing rows backfill via the DB-level CURRENT_TIMESTAMP default; new rows
-- are set explicitly by the User entity constructor (Instant.now()).
--
-- This file is the forward migration path. Flyway is not yet wired in the
-- build; once it is, this script will be applied as V1. Until then, run this
-- manually before deploying the build that introduces User.createdAt to any
-- environment with prior `users` rows. Greenfield deploys are unaffected
-- because Hibernate ddl-auto=validate will see the column from CREATE TABLE.

ALTER TABLE users
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
