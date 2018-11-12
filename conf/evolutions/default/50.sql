#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE problems ADD COLUMN points INT NOT NULL DEFAULT 0;

UPDATE problems
SET points = tc.sumPoints
FROM (
       SELECT c.problem_id, sum(c.points) sumPoints
       FROM testcases c
       GROUP BY c.problem_id
     ) tc
WHERE tc.problem_id = id;

# --- !Downs

ALTER TABLE problems DROP COLUMN points;
