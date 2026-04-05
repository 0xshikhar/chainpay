-- V3: Add calldata and value_sent_wei to blockchain_transactions
-- Required for gas bump re-broadcast to reconstruct exact original transactions
-- Without these columns, GasManagementService cannot replay the correct calldata
-- and falls back to a plain ETH transfer instead of the original contract call.

ALTER TABLE blockchain_transactions
    ADD COLUMN IF NOT EXISTS calldata TEXT,
    ADD COLUMN IF NOT EXISTS value_sent_wei NUMERIC(38, 0);

COMMENT ON COLUMN blockchain_transactions.calldata IS
    'ABI-encoded hex calldata of the original transaction. Used to reconstruct exact replacement tx during gas bumping.';
COMMENT ON COLUMN blockchain_transactions.value_sent_wei IS
    'Original msg.value (in wei) sent with the transaction. Preserved for accurate gas bump re-broadcast.';
