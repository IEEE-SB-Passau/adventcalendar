# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE feedback
  ALTER COLUMN pro TYPE text,
  ALTER COLUMN con TYPE text,
  ALTER COLUMN freetext TYPE text;

# --- !Downs
