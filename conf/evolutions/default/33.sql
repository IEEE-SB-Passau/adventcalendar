# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE postings ADD COLUMN lang VARCHAR(10) DEFAULT 'de';
ALTER TABLE postings DROP CONSTRAINT postings_pkey;
ALTER TABLE postings ADD PRIMARY KEY (id, lang);

# --- !Downs

DELETE FROM postings WHERE lang NOT LIKE 'de';
ALTER TABLE postings DROP CONSTRAINT postings_pkey;
ALTER TABLE postings DROP COLUMN lang;
ALTER TABLE postings ADD PRIMARY KEY (id);
