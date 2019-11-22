# DEFAULT SCHEMA

# --- !Ups

UPDATE postings SET id = 9 WHERE id = 2 AND lang = 'en';
UPDATE postings SET id = 2, title= 'calendar' WHERE id = 3 AND lang = 'en';
UPDATE postings SET id = 3, title = 'status' WHERE id = 9 AND lang = 'en';

# --- !Downs

UPDATE postings SET id = 9 WHERE id = 2 AND lang = 'en';
UPDATE postings SET id = 2, title= 'status' WHERE id = 3 AND lang = 'en';
UPDATE postings SET id = 3, title = 'calendar' WHERE id = 9 AND lang = 'en';
