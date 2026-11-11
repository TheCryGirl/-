-- Additive migration; no changes to users, roles, status or passwords.

-- Revocation version survives role/status round trips and process restarts.
CREATE TABLE IF NOT EXISTS user_auth_version (
    user_id VARCHAR(32) NOT NULL PRIMARY KEY,
    token_version BIGINT NOT NULL DEFAULT 0
);
