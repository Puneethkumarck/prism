CREATE TABLE IF NOT EXISTS staging_transactions (
    signature VARCHAR(88),
    slot      BIGINT,
    success   BOOLEAN
);
