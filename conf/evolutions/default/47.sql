#
DEFAULT
SCHEMA

# --- !Ups

INSERT INTO problem_translations (problem_id, language_code, title, description)
SELECT p.id, 'en', p.title, p.description
FROM problems as p
       LEFT JOIN (SELECT id FROM problems INTERSECT SELECT problem_id FROM problem_translations) AS q ON p.id = q.id
WHERE NOT EXISTS(
    SELECT problem_id, language_code
    FROM problem_translations pt
    WHERE pt.problem_id = p.id AND pt.language_code = 'en');

ALTER TABLE problems DROP COLUMN description, DROP COLUMN title;

# --- !Downs

ALTER TABLE problems
  ADD COLUMN title CHARACTER VARYING(200) NOT NULL DEFAULT '',
  ADD COLUMN description TEXT NOT NULL DEFAULT '';

UPDATE problems p
  SET title = pt.title, description = pt.description
  FROM problem_translations pt WHERE pt.problem_id = p.id and pt.language_code = 'en';
