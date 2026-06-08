-- ═══════════════════════════════════════════════════════════════════════════
-- V2__create_app_users.sql — Flyway Migration
-- ═══════════════════════════════════════════════════════════════════════════
--
-- SEEDED USERS:
--   admin@school.com / admin123   → Role: ADMIN
--   staff@school.com / staff123   → Role: STAFF

CREATE TABLE IF NOT EXISTS app_users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    created_at TIMESTAMP    DEFAULT NOW()
);

-- Seed admin user
-- Password: admin123
INSERT INTO app_users (email, password, role) VALUES (
    'admin@school.com',
    '$2a$10$8T.7oYlE8zsPtOKBjD3uSO58yKWNhUmsgLdMO4F8300pC3.glNamO',
    'ADMIN'
);

-- Seed staff user
-- Password: staff123
INSERT INTO app_users (email, password, role) VALUES (
    'staff@school.com',
    '$2a$10$v97kpgSNmJwlmcz.sV2SseBJGoFgPkcM8LlJpIq/W53Nzvp30rgby',
    'STAFF'
);