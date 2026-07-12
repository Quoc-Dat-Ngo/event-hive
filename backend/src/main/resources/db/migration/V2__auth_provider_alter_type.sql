CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE');

ALTER TABLE users
    ALTER COLUMN auth_provider TYPE auth_provider USING auth_provider::auth_provider,
    ALTER COLUMN auth_provider SET NOT NULL;