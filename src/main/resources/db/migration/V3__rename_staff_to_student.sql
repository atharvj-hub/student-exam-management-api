-- ═══════════════════════════════════════════════════════════════════════════
-- V3__rename_staff_to_student.sql — Flyway Migration
-- ═══════════════════════════════════════════════════════════════════════════
--
-- PURPOSE:
--   The role name "STAFF" has been renamed to "STUDENT" to better reflect
--   the actual user types in a student exam management system.
--
-- WHAT THIS DOES:
--   1. Drops the existing CHECK constraint on the role column (which only allowed ADMIN/STAFF)
--   2. Updates all existing STAFF records to STUDENT
--   3. Re-adds the CHECK constraint allowing ADMIN/STUDENT
--   4. Updates the seeded staff@school.com user to reflect the new role name
--
-- NOTE: Cannot modify V2 directly — Flyway checksums already-applied migrations
-- and will reject any modification to them. All database changes must be new
-- versioned files.

-- Step 1: Drop the old CHECK constraint that only allowed 'STAFF'
ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check;

-- Step 2: Update all existing STAFF records → STUDENT
UPDATE app_users SET role = 'STUDENT' WHERE role = 'STAFF';

-- Step 3: Add a new CHECK constraint allowing ADMIN and STUDENT
ALTER TABLE app_users
    ADD CONSTRAINT app_users_role_check CHECK (role IN ('ADMIN', 'STUDENT'));
