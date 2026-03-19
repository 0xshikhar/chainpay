-- V2__add_incidents_and_ops.sql: Migration for Phase 8 Gas Management and Phase 9 AI Ops Incidents

CREATE TABLE operational_incidents (
    id UUID PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- OPEN, INVESTIGATING, RESOLVED, AUTO_HEALED
    resolution_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE hot_wallet_nodes (
    id UUID PRIMARY KEY,
    address VARCHAR(64) NOT NULL UNIQUE,
    current_nonce BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operational_incidents_status ON operational_incidents(status);
