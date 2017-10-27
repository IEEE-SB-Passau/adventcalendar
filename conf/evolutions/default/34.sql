# DEFAULT SCHEMA

# --- !Ups

INSERT INTO postings (id, title, content, lang, date) VALUES
  (3, 'Status', 'Die Auswertung ist im Moment angehalten, bitte habe etwas Geduld, das Team kümmert sich darum!<br>', 'de', now()),
  (3, 'Execution Status', 'The execution is currently paused because we are working on the system. Please be patient!<br>', 'en', now());

# --- !Downs

DELETE FROM postings WHERE id IS 3;

