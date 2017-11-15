# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE problems ADD COLUMN cpu_factor FLOAT DEFAULT 1;
ALTER TABLE problems ADD COLUMN mem_factor FLOAT DEFAULT 1;

# --- !Downs

ALTER TABLE problems DROP COLUMN cpu_factor;
ALTER TABLE problems DROP COLUMN mem_factor;