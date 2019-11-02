#
DEFAULT
SCHEMA

# --- !Ups

ALTER TABLE solutions RENAME TO solutions_no_score;
ALTER TABLE solutions_no_score DROP COLUMN score;
ALTER TABLE solutions_no_score DROP COLUMN result;

CREATE OR REPLACE VIEW solutions AS
SELECT s.id,
       s.user_id,
       s.problem_id,
       s.created,
       s.language,
       s.program,
       s.program_name,
       0          score,
       'CANCELED' result
FROM solutions_no_score s;

CREATE OR REPLACE RULE "_RETURN" AS
  ON SELECT TO solutions
  DO INSTEAD
  SELECT s.id,
         s.user_id,
         s.problem_id,
         s.created,
         s.language,
         s.program,
         s.program_name,
         coalesce(tc1.sumPoints::INTEGER, 0) score,
         (CASE
            WHEN 'PASSED' = ALL (tc2.result) THEN 'PASSED'
            WHEN 'QUEUED' = ANY (tc2.result) THEN 'QUEUED'
            WHEN 'COMPILER_ERROR' = ANY (tc2.result) THEN 'COMPILER_ERROR'
            WHEN 'PROGRAM_ERROR' = ANY (tc2.result) THEN 'PROGRAM_ERROR'
            WHEN 'RUNTIME_EXCEEDED' = ANY (tc2.result) THEN 'RUNTIME_EXCEEDED'
            WHEN 'MEMORY_EXCEEDED' = ANY (tc2.result) THEN 'MEMORY_EXCEEDED'
            WHEN 'CANCELED' = ANY (tc2.result) THEN 'CANCELED'
            ELSE 'WRONG_ANSWER'
           END) AS result
  FROM solutions_no_score s
  LEFT OUTER JOIN (
    SELECT r.solution_id, sum(c.points)::INTEGER sumPoints
    FROM testcases c,
         testruns r
    WHERE r.testcase_id = c.id
      AND r.result = 'PASSED'
    GROUP BY r.solution_id
  ) tc1 ON (s.id = tc1.solution_id)
  LEFT OUTER JOIN (
    SELECT r.solution_id, array_agg(r.result) result
    FROM testcases c,
         testruns r
    WHERE r.testcase_id = c.id
    GROUP BY r.solution_id
  ) tc2 ON (s.id = tc2.solution_id);

CREATE OR REPLACE RULE insert_solution AS
  ON INSERT TO solutions
  DO INSTEAD
  INSERT INTO solutions_no_score (user_id, problem_id, created,
                                  language, program, program_name)
  VALUES (new.user_id, new.problem_id, new.created,
          new.language, new.program, new.program_name)
  RETURNING solutions_no_score.id,
            solutions_no_score.user_id,
            solutions_no_score.problem_id,
            solutions_no_score.created,
            solutions_no_score.language,
            solutions_no_score.program,
            solutions_no_score.program_name,
            0        score,
            'QUEUED' result;

CREATE OR REPLACE RULE update_solution AS
  ON UPDATE TO solutions
  DO INSTEAD
  UPDATE solutions_no_score
  SET user_id      = new.user_id,
      problem_id   = new.problem_id,
      created      = new.created,
      language     = new.language,
      program      = new.program,
      program_name = new.program_name
  WHERE id = new.id;

CREATE OR REPLACE RULE delete_solution AS
  ON DELETE TO solutions
  DO INSTEAD
  DELETE
  FROM solutions_no_score s
  WHERE s.id = old.id;

ALTER TABLE problems RENAME TO problems_no_points;
ALTER TABLE problems_no_points DROP COLUMN points;

CREATE OR REPLACE VIEW problems AS
SELECT p.id,
       p.door,
       p.readable_start,
       p.readable_stop,
       p.solvable_start,
       p.solvable_stop,
       p.eval_mode,
       p.cpu_factor,
       p.mem_factor,
       0 points
FROM problems_no_points p;

CREATE OR REPLACE RULE "_RETURN" AS
  ON SELECT TO problems
  DO INSTEAD
  SELECT p.id,
         p.door,
         p.readable_start,
         p.readable_stop,
         p.solvable_start,
         p.solvable_stop,
         p.eval_mode,
         p.cpu_factor,
         p.mem_factor,
         coalesce(tc1.sumPoints::INTEGER, 0) points
  FROM problems_no_points p
  LEFT OUTER JOIN (
    SELECT c.problem_id, sum(c.points)::INTEGER sumPoints
    FROM testcases c
    GROUP BY c.problem_id
  ) tc1 ON (p.id = tc1.problem_id);

CREATE OR REPLACE RULE insert_problem AS
  ON INSERT TO problems
  DO INSTEAD
  INSERT INTO problems_no_points (door, readable_start, readable_stop, solvable_start,
                                  solvable_stop, eval_mode, cpu_factor, mem_factor)
  VALUES (new.door, new.readable_start, new.readable_stop, new.solvable_start,
          new.solvable_stop, new.eval_mode, new.cpu_factor, new.mem_factor)
  RETURNING problems_no_points.id,
            problems_no_points.door,
            problems_no_points.readable_start,
            problems_no_points.readable_stop,
            problems_no_points.solvable_start,
            problems_no_points.solvable_stop,
            problems_no_points.eval_mode,
            problems_no_points.cpu_factor,
            problems_no_points.mem_factor,
            0 points;

CREATE OR REPLACE RULE update_solution AS
  ON UPDATE TO problems
  DO INSTEAD
  UPDATE problems_no_points
  SET door           = new.door,
      readable_start = new.readable_start,
      readable_stop  = new.readable_stop,
      solvable_start = new.solvable_start,
      solvable_stop  = new.solvable_stop,
      eval_mode      = new.eval_mode,
      cpu_factor     = new.cpu_factor,
      mem_factor     = new.mem_factor
  WHERE id = new.id;

CREATE OR REPLACE RULE delete_problems AS
  ON DELETE TO problems
  DO INSTEAD
  DELETE
  FROM problems_no_points p
  WHERE p.id = old.id;

# --- !Downs

DROP VIEW problems;
DROP VIEW solutions;
ALTER TABLE problems_no_points RENAME TO problems;
ALTER TABLE solutions_no_score RENAME TO solutions;

ALTER TABLE problems ADD COLUMN points INT NOT NULL DEFAULT 0;

UPDATE problems
SET points = tc.sumPoints
FROM (
       SELECT c.problem_id, sum(c.points) sumPoints
       FROM testcases c
       GROUP BY c.problem_id
     ) tc
WHERE tc.problem_id = id;

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
