#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE feedback ADD COLUMN created TIMESTAMP NOT NULL DEFAULT now();

# --- !Downs

ALTER TABLE feedback DROP COLUMN created;
