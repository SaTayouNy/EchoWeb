-- A conflict in existing data intentionally stops migration instead of renaming accounts.
ALTER TABLE users ADD COLUMN username_normalized VARCHAR(160);
UPDATE users SET username_normalized = LOWER(TRIM(username));
ALTER TABLE users ALTER COLUMN username_normalized SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_username_normalized UNIQUE (username_normalized);
