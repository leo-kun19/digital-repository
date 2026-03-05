CREATE TABLE IF NOT EXISTS faculty (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32),
  name VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS program (
  id BIGSERIAL PRIMARY KEY,
  faculty_id BIGINT NOT NULL REFERENCES faculty(id),
  code VARCHAR(32),
  name VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO faculty (code, name, active)
VALUES
  ('FET', 'Faculty of Engineering & Technology', TRUE),
  ('FBS', 'Faculty of Business & Social Sciences', TRUE),
  ('FAS', 'Faculty of Arts & Science', TRUE);

INSERT INTO program (faculty_id, code, name, active)
VALUES
  ((SELECT id FROM faculty WHERE code = 'FET'), 'IS', 'Information Systems', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FET'), 'EE', 'Electrical Engineering', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FBS'), 'BA', 'Business Administration', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FBS'), 'ACC', 'Accounting', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FAS'), 'CS', 'Computer Science', TRUE);
