#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE solutions
  DROP COLUMN user_agent,
  DROP COLUMN browser_id,
  DROP COLUMN ip;

CREATE TABLE schools
(
  school VARCHAR(50)
);
INSERT INTO schools
SELECT school
FROM users;

ALTER TABLE users
  DROP COLUMN semester,
  DROP COLUMN study_subject,
  DROP COLUMN school;

# --- !Downs

ALTER TABLE solutions
  ADD COLUMN user_agent VARCHAR(150),
  ADD COLUMN browser_id VARCHAR(64),
  ADD COLUMN ip         VARCHAR(42);

ALTER TABLE users
  ADD COLUMN semester      INTEGER,
  ADD COLUMN study_subject VARCHAR(100),
  ADD COLUMN school        VARCHAR(50);


DROP TABLE schools;
