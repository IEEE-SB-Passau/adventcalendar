# DEFAULT SCHEMA

# --- !Ups

UPDATE postings SET id = 8 WHERE id = 1;
UPDATE postings SET id = 1 WHERE id = 3;
UPDATE postings SET id = 3 WHERE id = 8;

# --- !Downs

UPDATE postings SET id = 8 WHERE id = 1;
UPDATE postings SET id = 1 WHERE id = 3;
UPDATE postings SET id = 3 WHERE id = 8;
