#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE solutions ADD COLUMN score INT NOT NULL DEFAULT 0;

UPDATE solutions
SET score = tc.sumPoints
FROM (
       SELECT r.solution_id, sum(c.points) sumPoints
       FROM testcases c,
            testruns r
       WHERE r.testcase_id = c.id
         AND r.result = 'PASSED'
       GROUP BY r.solution_id
     ) tc
WHERE tc.solution_id = id;

# --- !Downs

ALTER TABLE solutions DROP COLUMN score;
