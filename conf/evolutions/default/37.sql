# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE tickets ADD COLUMN lang VARCHAR(10) DEFAULT 'de';

# --- !Downs

ALTER TABLE tickets DROP COLUMN lang;