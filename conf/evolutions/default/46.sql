#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE users ADD COLUMN notification_dismissed BOOLEAN DEFAULT FALSE;
INSERT INTO postings (id, title, content, lang, date) VALUES
    (8, 'notification', '', 'de', now()),
    (8, 'notification', '', 'en', now());

# --- !Downs

ALTER TABLE users DROP COLUMN notification_dismissed;
DELETE FROM postings WHERE id IS 8;