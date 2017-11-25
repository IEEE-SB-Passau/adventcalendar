# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE testruns ADD COLUMN prog_memory INTEGER DEFAULT 0;
ALTER TABLE testruns ADD COLUMN comp_memory INTEGER DEFAULT 0;

# --- !Downs

ALTER TABLE testruns DROP COLUMN prog_memory;
ALTER TABLE testruns DROP COLUMN comp_memory;