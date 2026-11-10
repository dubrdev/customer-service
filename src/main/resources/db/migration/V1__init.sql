CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    cpf         VARCHAR(11)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_customers_cpf UNIQUE (cpf)
);
CREATE INDEX idx_customers_status ON customers (status);
CREATE INDEX idx_customers_name_lower ON customers (lower(name));

-- Idempotency: every processed eventId is stored; the PK blocks duplicates.
CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);
