CREATE TABLE IF NOT EXISTS collection_orders (
    id               VARCHAR(30)   PRIMARY KEY,
    merchant_id      VARCHAR(30)   NOT NULL,
    status           VARCHAR(15)   NOT NULL,
    pending_balance  NUMERIC(19,2) NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_collection_orders_status  CHECK (status IN ('OPEN', 'BLOCKED')),
    CONSTRAINT ck_collection_orders_balance CHECK (pending_balance >= 0)
);

CREATE TABLE IF NOT EXISTS payment_reports (
    id                   BIGSERIAL     PRIMARY KEY,
    payment_reference    VARCHAR(50)   NOT NULL,
    collection_order_id  VARCHAR(30)   NOT NULL,
    merchant_id          VARCHAR(30)   NOT NULL,
    amount               NUMERIC(19,2) NOT NULL,
    status               VARCHAR(10)   NOT NULL,
    reason_code          VARCHAR(40)   NOT NULL,
    reason_message       VARCHAR(255)  NOT NULL,
    balance_after        NUMERIC(19,2),
    processed_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_payment_reports_reference UNIQUE (payment_reference),
    CONSTRAINT ck_payment_reports_status    CHECK (status IN ('APPLIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS ix_payment_reports_processed_at ON payment_reports (processed_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_payment_reports_order ON payment_reports (collection_order_id);
