# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE testruns ADD COLUMN eval_id VARCHAR DEFAULT '';

# --- !Downs

ALTER TABLE testruns DROP COLUMN eval_id;
