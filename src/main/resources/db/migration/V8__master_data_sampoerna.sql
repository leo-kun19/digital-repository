DELETE FROM program;
DELETE FROM faculty;

INSERT INTO faculty (code, name, active)
VALUES
  ('FET', 'Faculty of Engineering and Technology (FET)', TRUE),
  ('FOB', 'Faculty of Business (FOB)', TRUE),
  ('FOE', 'Faculty of Education (FOE)', TRUE),
  ('FAS', 'Faculty of Arts and Science (FAS)', TRUE);

INSERT INTO program (faculty_id, code, name, active)
VALUES
  ((SELECT id FROM faculty WHERE code = 'FET'), 'CS', 'Computer Science', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FET'), 'IS', 'Information System', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FET'), 'IE', 'Industrial Engineering', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FET'), 'ME', 'Mechanical Engineering', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FET'), 'VCD', 'Visual Communication Design', TRUE),

  ((SELECT id FROM faculty WHERE code = 'FOB'), 'MGT', 'Management', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FOB'), 'FIN', 'Finance', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FOB'), 'DM', 'Digital Marketing', TRUE),
  ((SELECT id FROM faculty WHERE code = 'FOB'), 'ACC', 'Accounting', TRUE),

  ((SELECT id FROM faculty WHERE code = 'FOE'), 'ENG', 'English Language', TRUE),

  ((SELECT id FROM faculty WHERE code = 'FAS'), 'PSY', 'Psychology', TRUE);
