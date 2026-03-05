CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS student_profile (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  student_number VARCHAR(64) UNIQUE NOT NULL,
  name VARCHAR(255),
  program VARCHAR(255),
  faculty VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS lecturer_profile (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  name VARCHAR(255),
  department VARCHAR(255),
  faculty VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS thesis (
  id BIGSERIAL PRIMARY KEY,
  student_id BIGINT REFERENCES users(id),
  title TEXT,
  abstract_text TEXT,
  keywords TEXT,
  file_path TEXT,
  submitted_at TIMESTAMP,
  current_status VARCHAR(64),
  year_published INTEGER,
  faculty VARCHAR(255),
  major VARCHAR(255),
  published_at TIMESTAMP,
  student_name VARCHAR(255),
  supervisor_name VARCHAR(255),
  program VARCHAR(255),
  submission_year INTEGER
);

CREATE TABLE IF NOT EXISTS approval (
  id BIGSERIAL PRIMARY KEY,
  thesis_id BIGINT REFERENCES thesis(id),
  stage VARCHAR(32),
  status VARCHAR(32),
  notes TEXT,
  decided_by BIGINT REFERENCES users(id),
  decided_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS checklist_item (
  id BIGSERIAL PRIMARY KEY,
  ckey VARCHAR(64) UNIQUE,
  label VARCHAR(128),
  category VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS thesis_checklist (
  id BIGSERIAL PRIMARY KEY,
  thesis_id BIGINT REFERENCES thesis(id),
  item_id BIGINT REFERENCES checklist_item(id),
  checked BOOLEAN,
  checked_by BIGINT REFERENCES users(id),
  checked_at TIMESTAMP,
  CONSTRAINT uq_thesis_checklist_pair UNIQUE (thesis_id, item_id)
);

CREATE TABLE IF NOT EXISTS supervisor_assignment (
  id BIGSERIAL PRIMARY KEY,
  lecturer_user_id BIGINT REFERENCES users(id),
  student_user_id BIGINT REFERENCES users(id),
  role_main BOOLEAN DEFAULT TRUE,
  CONSTRAINT uq_supervisor_pair UNIQUE (lecturer_user_id, student_user_id)
);

CREATE TABLE IF NOT EXISTS publication_case (
  id BIGSERIAL PRIMARY KEY,
  student_user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS publication_registration (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL UNIQUE REFERENCES publication_case(id),
  title VARCHAR(500) NOT NULL,
  publication_year INTEGER,
  article_publish_in VARCHAR(255),
  faculty VARCHAR(255),
  student_id_number VARCHAR(100),
  author_name VARCHAR(255),
  permission_accepted_at TIMESTAMP,
  submitted_at TIMESTAMP,
  supervisor_decision_at TIMESTAMP,
  supervisor_decision_note TEXT
);

CREATE TABLE IF NOT EXISTS case_supervisor (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES publication_case(id),
  lecturer_user_id BIGINT NOT NULL REFERENCES users(id),
  CONSTRAINT uq_case_supervisor UNIQUE (case_id, lecturer_user_id)
);

CREATE TABLE IF NOT EXISTS submission_version (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES publication_case(id),
  version_number INTEGER NOT NULL,
  file_path TEXT NOT NULL,
  original_filename VARCHAR(500) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  metadata_title VARCHAR(500),
  metadata_authors VARCHAR(500),
  metadata_keywords TEXT,
  metadata_faculty VARCHAR(255),
  metadata_year INTEGER,
  abstract_text TEXT,
  status VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_case_submission_version UNIQUE (case_id, version_number)
);

CREATE TABLE IF NOT EXISTS workflow_comment (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL REFERENCES publication_case(id),
  submission_version_id BIGINT REFERENCES submission_version(id),
  author_user_id BIGINT NOT NULL REFERENCES users(id),
  author_role VARCHAR(32) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clearance_form (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL UNIQUE REFERENCES publication_case(id),
  status VARCHAR(64) NOT NULL,
  submitted_at TIMESTAMP,
  approved_at TIMESTAMP,
  note TEXT
);

CREATE TABLE IF NOT EXISTS checklist_template (
  id BIGSERIAL PRIMARY KEY,
  publication_type VARCHAR(64) NOT NULL,
  version INTEGER NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_checklist_template UNIQUE (publication_type, version)
);

CREATE TABLE IF NOT EXISTS checklist_item_v2 (
  id BIGSERIAL PRIMARY KEY,
  template_id BIGINT NOT NULL REFERENCES checklist_template(id),
  order_index INTEGER NOT NULL,
  section VARCHAR(255),
  item_text TEXT NOT NULL,
  guidance_text TEXT,
  is_required BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS checklist_result (
  id BIGSERIAL PRIMARY KEY,
  submission_version_id BIGINT NOT NULL REFERENCES submission_version(id),
  checklist_item_id BIGINT NOT NULL REFERENCES checklist_item_v2(id),
  pass_fail VARCHAR(16) NOT NULL,
  note TEXT
);

CREATE TABLE IF NOT EXISTS published_item (
  id BIGSERIAL PRIMARY KEY,
  case_id BIGINT NOT NULL UNIQUE REFERENCES publication_case(id),
  submission_version_id BIGINT REFERENCES submission_version(id),
  published_at TIMESTAMP NOT NULL,
  title VARCHAR(500) NOT NULL,
  authors VARCHAR(500) NOT NULL,
  author_name VARCHAR(255),
  faculty VARCHAR(255),
  publication_year INTEGER,
  keywords TEXT,
  abstract_text TEXT
);

CREATE INDEX IF NOT EXISTS idx_repo_title ON published_item (title);
CREATE INDEX IF NOT EXISTS idx_repo_authors ON published_item (authors);
CREATE INDEX IF NOT EXISTS idx_repo_faculty ON published_item (faculty);
CREATE INDEX IF NOT EXISTS idx_repo_year ON published_item (publication_year);
CREATE INDEX IF NOT EXISTS idx_repo_keywords ON published_item (keywords);
