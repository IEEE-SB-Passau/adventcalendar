# DEFAULT SCHEMA

# --- !Ups

INSERT INTO postings (id, title, content, lang, date) VALUES
  (4, 'faq', '', 'de', now()),
  (4, 'faq', '', 'en', now()),
  (5, 'rules', '', 'de', now()),
  (5, 'rules', '', 'en', now()),
  (6, 'examples', '', 'de', now()),
  (6, 'examples', '', 'en', now()),
  (7, 'contact', '', 'de', now()),
  (7, 'contact', '', 'en', now());

# --- !Downs

DELETE FROM postings WHERE id IN (4, 5, 6, 7);

