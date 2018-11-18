#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE e_prog_lang ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

# --- !Downs

ALTER TABLE e_prog_lang DROP COLUMN active;
