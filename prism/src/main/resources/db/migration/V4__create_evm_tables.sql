CREATE TABLE IF NOT EXISTS evm_blocks (
    block_number      BIGINT PRIMARY KEY,
    chain_id          INTEGER NOT NULL,
    block_hash        VARCHAR(66) NOT NULL UNIQUE,
    parent_hash       VARCHAR(66) NOT NULL,
    timestamp         TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_count INTEGER NOT NULL,
    gas_used          BIGINT NOT NULL,
    gas_limit         BIGINT NOT NULL,
    base_fee_per_gas  NUMERIC,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_transactions (
    tx_hash             VARCHAR(66) PRIMARY KEY,
    chain_id            INTEGER NOT NULL,
    block_number        BIGINT NOT NULL,
    block_hash          VARCHAR(66) NOT NULL,
    tx_index            INTEGER NOT NULL,
    from_address        VARCHAR(42) NOT NULL,
    to_address          VARCHAR(42),
    value               NUMERIC NOT NULL,
    status              BOOLEAN NOT NULL,
    gas_used            BIGINT NOT NULL,
    effective_gas_price NUMERIC NOT NULL,
    tx_type             SMALLINT NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_failed_transactions (
    id            SERIAL PRIMARY KEY,
    tx_hash       VARCHAR(66) NOT NULL,
    block_number  BIGINT NOT NULL,
    from_address  VARCHAR(42) NOT NULL,
    to_address    VARCHAR(42),
    gas_used      BIGINT NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_large_transfers (
    id            SERIAL PRIMARY KEY,
    tx_hash       VARCHAR(66) NOT NULL,
    block_number  BIGINT NOT NULL,
    from_address  VARCHAR(42) NOT NULL,
    to_address    VARCHAR(42),
    value         NUMERIC NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_token_transfers (
    id             SERIAL PRIMARY KEY,
    tx_hash        VARCHAR(66) NOT NULL,
    block_number   BIGINT NOT NULL,
    log_index      INTEGER NOT NULL,
    token_address  VARCHAR(42) NOT NULL,
    from_address   VARCHAR(42) NOT NULL,
    to_address     VARCHAR(42) NOT NULL,
    value          NUMERIC NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_accounts (
    id                SERIAL PRIMARY KEY,
    address           VARCHAR(42) NOT NULL UNIQUE,
    block_number      BIGINT NOT NULL,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    first_seen        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evm_staging_transactions (
    tx_hash             VARCHAR(66),
    chain_id            INTEGER,
    block_number        BIGINT,
    block_hash          VARCHAR(66),
    tx_index            INTEGER,
    from_address        VARCHAR(42),
    to_address          VARCHAR(42),
    value               NUMERIC,
    status              BOOLEAN,
    gas_used            BIGINT,
    effective_gas_price NUMERIC,
    tx_type             SMALLINT
);
