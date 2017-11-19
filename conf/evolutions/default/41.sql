# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE users ADD COLUMN is_system BOOLEAN DEFAULT FALSE;
UPDATE users SET is_system = TRUE WHERE users.username = '__BACKEND__';
UPDATE users SET is_admin = FALSE WHERE users.username = '__BACKEND__';

# --- !Downs

ALTER TABLE users DROP COLUMN is_system;
UPDATE users SET is_admin = TRUE WHERE users.username = '__BACKEND__';