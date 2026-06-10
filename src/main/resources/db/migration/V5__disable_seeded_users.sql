-- ═══════════════════════════════════════════════════════════════════════════
-- V5__disable_seeded_users.sql — Flyway Migration
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Replaces the seeded passwords with an invalid hash string to effectively
-- disable the accounts from logging in. This is a migration-safe approach
-- that preserves historical IDs without dropping rows.

UPDATE app_users 
SET password = '*DISABLED*' 
WHERE email IN ('admin@school.com', 'staff@school.com');
