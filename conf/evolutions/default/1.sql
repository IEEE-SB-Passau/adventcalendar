# DEFAULT SCHEMA

# --- !Ups

CREATE TABLE e_prog_lang (
  language        CHARACTER VARYING(30) PRIMARY KEY,
  name            CHARACTER VARYING(196)                                NOT NULL,
  highlight_class CHARACTER VARYING(30) DEFAULT '' :: CHARACTER VARYING NOT NULL
);

CREATE TABLE e_test_eval_mode (
  mode CHARACTER VARYING(20) PRIMARY KEY
);

CREATE TABLE e_test_result (
  result CHARACTER VARYING(30) PRIMARY KEY
);

CREATE TABLE e_test_visibility (
  scope CHARACTER VARYING(20) PRIMARY KEY
);

CREATE TABLE postings (
  id      SERIAL PRIMARY KEY,
  title   CHARACTER VARYING,
  content CHARACTER VARYING,
  date    DATE NOT NULL
);

CREATE TABLE users (
  id            SERIAL PRIMARY KEY,
  username      CHARACTER VARYING(30)  NOT NULL UNIQUE,
  password      CHARACTER VARYING(100) NOT NULL,
  email         CHARACTER VARYING(150) NOT NULL UNIQUE,
  is_active     BOOLEAN DEFAULT TRUE   NOT NULL,
  is_admin      BOOLEAN DEFAULT FALSE  NOT NULL,
  semester      INTEGER,
  study_subject CHARACTER VARYING(100),
  is_hidden     BOOLEAN DEFAULT FALSE  NOT NULL,
  token         CHARACTER VARYING(128) DEFAULT NULL :: CHARACTER VARYING,
  school        CHARACTER VARYING(50)
);

CREATE TABLE problems (
  id             SERIAL PRIMARY KEY,
  title          CHARACTER VARYING(200)      NOT NULL,
  door           INTEGER                     NOT NULL UNIQUE,
  description    TEXT                        NOT NULL,
  readable_start TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  readable_stop  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  solvable_start TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  solvable_stop  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  eval_mode      CHARACTER VARYING(20) DEFAULT 'STATIC' :: CHARACTER VARYING REFERENCES e_test_eval_mode (mode)
);

CREATE TABLE testcases (
  id              SERIAL PRIMARY KEY,
  problem_id      INTEGER NOT NULL REFERENCES problems (id) ON UPDATE CASCADE ON DELETE CASCADE,
  "position"      INTEGER NOT NULL,
  input           TEXT    NOT NULL,
  expected_output TEXT    NOT NULL,
  points          INTEGER NOT NULL,
  visibility      CHARACTER VARYING(20) DEFAULT NULL :: CHARACTER VARYING REFERENCES e_test_visibility (scope)
);

ALTER TABLE ONLY testcases
  ADD CONSTRAINT testcases_problem_id_position_key UNIQUE (problem_id, "position");

CREATE TABLE eval_task (
  id                    SERIAL PRIMARY KEY,
  problem_id            INTEGER                NOT NULL REFERENCES problems (id) ON UPDATE CASCADE ON DELETE CASCADE,
  "position"            INTEGER                NOT NULL,
  command               CHARACTER VARYING(512) NOT NULL,
  filename              CHARACTER VARYING(256) NOT NULL,
  file                  BYTEA                  NOT NULL,
  output_check          BOOLEAN DEFAULT FALSE  NOT NULL,
  score_calc            BOOLEAN DEFAULT FALSE  NOT NULL,
  use_stdin             BOOLEAN DEFAULT FALSE  NOT NULL,
  use_expout            BOOLEAN DEFAULT FALSE  NOT NULL,
  use_prevout           BOOLEAN DEFAULT FALSE  NOT NULL,
  use_prog              BOOLEAN DEFAULT FALSE  NOT NULL,
  run_on_correct_result BOOLEAN DEFAULT TRUE,
  run_on_wrong_result   BOOLEAN DEFAULT TRUE
);

CREATE TABLE solutions (
  id           SERIAL PRIMARY KEY,
  user_id      INTEGER                                   NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
  problem_id   INTEGER                                   NOT NULL REFERENCES problems (id) ON UPDATE CASCADE ON DELETE CASCADE,
  created      TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  language     CHARACTER VARYING(30)                     NOT NULL REFERENCES e_prog_lang (language) ON UPDATE RESTRICT ON DELETE RESTRICT,
  program      TEXT                                      NOT NULL,
  ip           CHARACTER VARYING(42),
  user_agent   CHARACTER VARYING(150),
  browser_id   CHARACTER VARYING(64),
  program_name CHARACTER VARYING DEFAULT 'Solution' :: CHARACTER VARYING
);

CREATE TABLE testruns (
  id           SERIAL PRIMARY KEY,
  solution_id  INTEGER                                   NOT NULL REFERENCES solutions (id) ON UPDATE CASCADE ON DELETE CASCADE,
  testcase_id  INTEGER                                   NOT NULL REFERENCES testcases (id) ON UPDATE CASCADE ON DELETE CASCADE,
  prog_out     TEXT,
  prog_err     TEXT,
  prog_exit    INTEGER,
  prog_runtime REAL,
  comp_out     TEXT,
  comp_err     TEXT,
  comp_exit    INTEGER,
  comp_runtime REAL,
  result       CHARACTER VARYING(30)                     NOT NULL REFERENCES e_test_result (result) ON UPDATE RESTRICT ON DELETE RESTRICT,
  created      TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  vm           CHARACTER VARYING(50) DEFAULT '' :: CHARACTER VARYING,
  completed    TIMESTAMP WITHOUT TIME ZONE,
  stage        INTEGER,
  score        INTEGER
);

CREATE TABLE tickets (
  id            SERIAL PRIMARY KEY,
  problem_id    INTEGER REFERENCES problems (id) ON UPDATE CASCADE ON DELETE CASCADE,
  user_id       INTEGER REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL,
  parent_ticket INTEGER REFERENCES tickets (id) ON UPDATE CASCADE ON DELETE CASCADE,
  ticket_text   TEXT                 NOT NULL,
  is_public     BOOLEAN DEFAULT TRUE NOT NULL,
  created       TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

CREATE TABLE feedback (
  id       SERIAL PRIMARY KEY,
  user_id  INTEGER NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
  rating   INTEGER NOT NULL,
  pro      CHARACTER VARYING(30),
  con      CHARACTER VARYING(30),
  freetext CHARACTER VARYING(30)
);

INSERT INTO e_prog_lang VALUES ('C', 'C, GCC 4.9.3, Standard: C11, gegen libm gelinkt', 'c');
INSERT INTO e_prog_lang VALUES ('COBOL', 'COBOL, OpenCOBOL 1.1, Free Format', '');
INSERT INTO e_prog_lang VALUES ('CPP', 'C++, GCC 4.9.3, Standard: C++11, gegen libm gelinkt', 'cpp');
INSERT INTO e_prog_lang VALUES ('CSHARP', 'C#, msc 2.10.9-r2, Mono Project', 'cs');
INSERT INTO e_prog_lang VALUES ('D', 'DLang, version 0.17.1-r1', '');
INSERT INTO e_prog_lang VALUES ('FORTH', 'Forth, Gforth 0.7.3', '');
INSERT INTO e_prog_lang VALUES ('FORTRAN', 'Fortran, GCC 4.9.3, Standard: Fortran 90', 'f90');
INSERT INTO e_prog_lang VALUES ('FREEPASCAL', 'FreePascal, fpc 2.4.6', '');
INSERT INTO e_prog_lang VALUES ('GO', 'Go, Go 1.4.2', 'go');
INSERT INTO e_prog_lang VALUES ('HASKELL', 'Haskell, The Haskell Platform 2013.2.0.0-r2, ghc 7.8.4', 'hs');
INSERT INTO e_prog_lang VALUES ('INTERCAL', 'Intercal, C-INTERCAL 29.0', '');
INSERT INTO e_prog_lang VALUES ('JAVA', 'Java, Oracle Java SE Development Kit 1.8.0.66', 'java');
INSERT INTO e_prog_lang VALUES ('JAVASCRIPT', 'JavaScript, Node.js v0.12.06', 'js');
INSERT INTO e_prog_lang VALUES ('LUA', 'Lua, Lua 5.1.5-r3', 'lua');
INSERT INTO e_prog_lang VALUES ('MMIX', 'MMIX, MMIX 20131017', '');
INSERT INTO e_prog_lang VALUES ('OCAML', 'OCaml, OCaml 4.02.3', 'ocaml');
INSERT INTO e_prog_lang VALUES ('OBJECTIVEC', 'Objective C, GCC 4.9.3, Standard: C99, gegen libm gelinkt, kompiliert mit -lobjc', 'objc');
INSERT INTO e_prog_lang VALUES ('PERL', 'Perl, Perl 5.20.2', 'pl');
INSERT INTO e_prog_lang VALUES ('PHP', 'PHP, PHP 5.6.1', 'php');
INSERT INTO e_prog_lang VALUES ('PYTHON2', 'Python 2, Python 2.7.10', 'py');
INSERT INTO e_prog_lang VALUES ('PYTHON3', 'Python 3, Python 3.4.3', 'py');
INSERT INTO e_prog_lang VALUES ('RUBY', 'Ruby, Ruby 2.1.7p400', 'rb');
INSERT INTO e_prog_lang VALUES ('RUST', 'Rust, Rust 1.3.0', 'rs');
INSERT INTO e_prog_lang VALUES ('SCALA', 'Scala, Scala 2.11.7-r1', 'scala');
INSERT INTO e_prog_lang VALUES ('TCL', 'Tool Command Language, TCL 8.5.17', 'tcl');
INSERT INTO e_prog_lang VALUES ('VALA', 'Vala, Vala 0.28.1', 'vala');
INSERT INTO e_prog_lang VALUES ('VISUALBASIC', 'Visual Basic .Net, vbnc 2.10, Mono Project', 'vb');

INSERT INTO e_test_eval_mode VALUES ('STATIC');
INSERT INTO e_test_eval_mode VALUES ('DYNAMIC');
INSERT INTO e_test_eval_mode VALUES ('BEST');
INSERT INTO e_test_eval_mode VALUES ('NO_EVAL');

INSERT INTO e_test_result VALUES ('QUEUED');
INSERT INTO e_test_result VALUES ('PASSED');
INSERT INTO e_test_result VALUES ('WRONG_ANSWER');
INSERT INTO e_test_result VALUES ('PROGRAM_ERROR');
INSERT INTO e_test_result VALUES ('MEMORY_EXCEEDED');
INSERT INTO e_test_result VALUES ('RUNTIME_EXCEEDED');
INSERT INTO e_test_result VALUES ('COMPILER_ERROR');
INSERT INTO e_test_result VALUES ('CANCELED');

INSERT INTO e_test_visibility VALUES ('PUBLIC');
INSERT INTO e_test_visibility VALUES ('PRIVATE');
INSERT INTO e_test_visibility VALUES ('HIDDEN');

INSERT INTO postings VALUES (1, 'Status', 'Hier könnte Ihre Werbung stehen!', '2016-11-10');
INSERT INTO postings VALUES (2, 'Adventskalender', '<p>Willkommen zum Adventskalender der IEEE Student Branch Passau!</p>', '2016-11-10');

# --- !Downs

DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS testruns CASCADE;
DROP TABLE IF EXISTS solutions CASCADE;
DROP TABLE IF EXISTS problems CASCADE;
DROP TABLE IF EXISTS testcases CASCADE;
DROP TABLE IF EXISTS eval_task CASCADE;
DROP TABLE IF EXISTS feedback CASCADE;
DROP TABLE IF EXISTS postings CASCADE;
DROP TABLE IF EXISTS tickets CASCADE;
DROP TABLE IF EXISTS e_prog_lang CASCADE;
DROP TABLE IF EXISTS e_test_result CASCADE;
DROP TABLE IF EXISTS e_test_eval_mode CASCADE;
DROP TABLE IF EXISTS e_test_visibility CASCADE;
