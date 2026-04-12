CREATE INDEX IF NOT EXISTS idx_evm_tx_block ON evm_transactions(block_number, tx_index);
CREATE INDEX IF NOT EXISTS idx_evm_tx_from ON evm_transactions(from_address, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_evm_tx_to ON evm_transactions(to_address, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_evm_tx_created ON evm_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_evm_tx_status ON evm_transactions(status, block_number DESC);

CREATE INDEX IF NOT EXISTS idx_evm_transfers_value ON evm_large_transfers(value DESC);
CREATE INDEX IF NOT EXISTS idx_evm_transfers_created ON evm_large_transfers(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_evm_token_tx ON evm_token_transfers(tx_hash);
CREATE INDEX IF NOT EXISTS idx_evm_token_address ON evm_token_transfers(token_address, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_evm_token_from ON evm_token_transfers(from_address, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_evm_token_to ON evm_token_transfers(to_address, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_evm_token_created ON evm_token_transfers(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_evm_blocks_created ON evm_blocks(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_evm_failed_created ON evm_failed_transactions(created_at DESC);
