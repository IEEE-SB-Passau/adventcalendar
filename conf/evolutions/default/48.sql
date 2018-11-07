#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE solutions ADD COLUMN score INT NOT NULL DEFAULT 0;

# --- !Downs

ALTER TABLE solutions DROP COLUMN score;
