# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE users ADD COLUMN language VARCHAR;

# --- !Downs

ALTER TABLE users DROP COLUMN language;
