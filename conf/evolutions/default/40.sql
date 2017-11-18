# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE e_prog_lang ADD COLUMN comment VARCHAR DEFAULT '';

# --- !Downs

ALTER TABLE e_prog_lang DROP COLUMN comment;