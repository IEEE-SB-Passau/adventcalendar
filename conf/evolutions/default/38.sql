# DEFAULT SCHEMA

# --- !Ups

ALTER TABLE e_prog_lang ADD COLUMN extension VARCHAR(10) DEFAULT '';
ALTER TABLE e_prog_lang ADD COLUMN cpu_factor FLOAT DEFAULT 1;
ALTER TABLE e_prog_lang ADD COLUMN mem_factor FLOAT DEFAULT 1;

# --- !Downs

ALTER TABLE e_prog_lang DROP COLUMN extension;
ALTER TABLE e_prog_lang DROP COLUMN cpu_factor;
ALTER TABLE e_prog_lang DROP COLUMN mem_factor;