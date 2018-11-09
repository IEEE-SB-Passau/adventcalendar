#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE solutions ADD COLUMN score INT NOT NULL DEFAULT 0;
ALTER TABLE solutions
  ADD COLUMN result VARCHAR(30) NOT NULL DEFAULT 'QUEUED'
    CONSTRAINT testruns_result_fkey
      REFERENCES e_test_result
      ON UPDATE RESTRICT ON DELETE RESTRICT;

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

UPDATE solutions
SET result = CASE
               WHEN 'PASSED' = ALL (tc.result) THEN 'PASSED'
               WHEN 'QUEUED' = ANY (tc.result) THEN 'QUEUED'
               WHEN 'COMPILER_ERROR' = ANY (tc.result) THEN 'COMPILER_ERROR'
               WHEN 'PROGRAM_ERROR' = ANY (tc.result) THEN 'PROGRAM_ERROR'
               WHEN 'RUNTIME_EXCEEDED' = ANY (tc.result) THEN 'RUNTIME_EXCEEDED'
               WHEN 'MEMORY_EXCEEDED' = ANY (tc.result) THEN 'MEMORY_EXCEEDED'
               WHEN 'CANCELED' = ANY (tc.result) THEN 'CANCELED'
               ELSE 'WRONG_ANSWER' END
FROM (
       SELECT r.solution_id, array_agg(r.result) result
       FROM testcases c,
            testruns r
            WHERE r.testcase_id = c.id
            GROUP BY r.solution_id
     ) tc
     WHERE tc.solution_id = id;

# --- !Downs

ALTER TABLE solutions DROP COLUMN score;
