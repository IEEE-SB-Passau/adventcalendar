# DEFAULT SCHEMA

# --- !Ups
CREATE TABLE e_permission (
  name CHARACTER VARYING(30) PRIMARY KEY
);

INSERT INTO e_permission VALUES ('EVERYONE');
INSERT INTO e_permission VALUES ('GUEST');
INSERT INTO e_permission VALUES ('CONTESTANT');
INSERT INTO e_permission VALUES ('MODERATOR');
INSERT INTO e_permission VALUES ('ADMIN');
INSERT INTO e_permission VALUES ('INTERNAL');

ALTER TABLE users ADD COLUMN permission CHARACTER VARYING(30) DEFAULT 'CONTESTANT' :: CHARACTER VARYING REFERENCES e_permission (name);
UPDATE users SET permission = 'ADMIN' WHERE is_admin;
UPDATE users SET permission = 'INTERNAL' WHERE is_system;
ALTER TABLE users DROP COLUMN is_admin;
ALTER TABLE users DROP COLUMN is_system;

# --- !Downs
ALTER TABLE users ADD COLUMN is_admin BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN is_system BOOLEAN DEFAULT FALSE;

UPDATE users SET is_admin = true WHERE permission = 'ADMIN';
UPDATE users SET is_system = true WHERE permission = 'INTERNAL';

ALTER TABLE users DROP COLUMN permission;

DROP TABLE IF EXISTS e_permission;
