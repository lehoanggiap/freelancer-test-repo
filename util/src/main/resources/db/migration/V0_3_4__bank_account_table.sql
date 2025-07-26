CREATE TABLE bank_accounts
(
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      INTEGER     NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    country_code   CHAR(2)     NOT NULL,
    bank_code      VARCHAR(20) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    UNIQUE (tenant_id, country_code, bank_code, account_number)
);

CREATE INDEX idx_bank_accounts_user_id ON bank_accounts (tenant_id);
