# DEFAULT SCHEMA

# --- !Ups

CREATE TABLE problem_translations (
  problem_id    INTEGER                REFERENCES problems (id) ON UPDATE CASCADE ON DELETE CASCADE,
  language_code VARCHAR(10)            NOT NULL,
  title         CHARACTER VARYING(200) NOT NULL,
  description   TEXT                   NOT NULL,

  PRIMARY KEY(problem_id, language_code)
)

# --- !Downs

DROP TABLE problem_translations;
