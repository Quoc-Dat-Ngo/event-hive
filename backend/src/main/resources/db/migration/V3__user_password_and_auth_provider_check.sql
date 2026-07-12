ALTER TABLE users
ADD CONSTRAINT chk_password_or_oauth
CHECK (
    (auth_provider = 'LOCAL' AND password_hash IS NOT NULL)
    OR 
    (auth_provider <> 'LOCAL' AND password_hash IS NULL)
);