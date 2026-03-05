-- Dev-only helper: wipe checklist templates and related data.
-- Order matters due to FK constraints.
DELETE FROM checklist_result;
DELETE FROM checklist_item_v2;
DELETE FROM checklist_template;
