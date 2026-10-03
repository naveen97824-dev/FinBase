-- =====================================================================
--  FINBASE — DATABASE SCHEMA  v2.0
--  Database: finbase
--  PostgreSQL 16+
--
--  REVISED per change request:
--    - Business PAN / Owner PAN now optional
--    - users table REMOVED (one shared login per company; auth lives on
--      financiers). All created_by / approved_by / collected_by FKs gone,
--      replaced by an optional free-text entered_by_name.
--    - Aadhaar number columns REMOVED entirely (photocopy only, stored as
--      a document with category 'aadhaar')
--    - Broker commission REMOVED
--    - Customer dedup on PAN + mobile only
--    - Loan approval workflow REMOVED (loans start ACTIVE)
--    - Penal charge: percentage OR fixed amount
--    - Interest scheme C (reducing balance) REMOVED
--    - Gold: net weight, deduction, purity test method, max LTV,
--      appraiser, seal number, storage location REMOVED
--    - promises_to_pay, visit_logs REMOVED
--    - notifications, notification_templates REMOVED (no reminders module)
--    - seizures heavily simplified; status enum removed
--
--  ARCHITECTURE
--    GLOBAL : financiers, vehicle_pledge_registry,
--             vehicle_inspection_queries, gold_rates, audit_log, sessions
--    TENANT : everything else, isolated by financier_id + Row Level
--             Security enforced at the DATABASE layer
--
--  MONEY  : NUMERIC(15,2). Never FLOAT/REAL.
--  WEIGHT : NUMERIC(10,3) grams
--  TIME   : TIMESTAMPTZ stored UTC, rendered Asia/Kolkata
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- =====================================================================
--  SECTION 1 — ENUM TYPES
-- =====================================================================

CREATE TYPE entity_type_enum      AS ENUM ('proprietorship','partnership','pvt_ltd','llp','individual');
CREATE TYPE financier_status_enum AS ENUM ('pending_verification','active','suspended','read_only','closed');

CREATE TYPE loan_type_enum        AS ENUM ('personal','vehicle','gold','document');
-- No draft / pending_approval / approved / rejected: loans start ACTIVE.
CREATE TYPE loan_status_enum      AS ENUM ('active','closed','foreclosed','seized','written_off');

-- Scheme C (reducing balance) removed.
CREATE TYPE interest_scheme_enum  AS ENUM ('A_monthly_interest_bullet','B_flat_emi','D_daily_collection');
CREATE TYPE rate_type_enum        AS ENUM ('per_month','per_annum');
CREATE TYPE penal_type_enum       AS ENUM ('percentage','fixed_amount','none');

CREATE TYPE frequency_enum        AS ENUM ('daily','weekly','fortnightly','monthly',
                                           'quarterly','half_yearly','yearly','bullet');
CREATE TYPE payment_mode_enum     AS ENUM ('cash','bank_transfer','upi','cheque','card');

CREATE TYPE schedule_status_enum  AS ENUM ('pending','due','partially_paid','paid','overdue','waived');
CREATE TYPE txn_type_enum         AS ENUM ('disbursal','repayment','interest_accrual','penal_charge',
                                           'charge','waiver','write_off','reversal','refund');

CREATE TYPE vehicle_type_enum     AS ENUM ('two_wheeler','three_wheeler','car','lcv','hcv',
                                           'tractor','construction_equipment','other');
CREATE TYPE gold_purity_enum      AS ENUM ('18K','20K','22K','24K');
CREATE TYPE pledge_status_enum    AS ENUM ('active','closed');

CREATE TYPE seizure_reason_enum   AS ENUM ('non_payment','willful_default','collateral_misuse',
                                           'asset_being_sold','other');

CREATE TYPE expense_kind_enum     AS ENUM ('running','petty');
CREATE TYPE payment_status_enum   AS ENUM ('pending','paid','partially_paid','skipped','overdue');

CREATE TYPE doc_category_enum     AS ENUM ('photo','aadhaar','pan','address_proof','rc_book','insurance',
                                           'driving_licence','permit','fitness_certificate','gold_photo',
                                           'land_deed','encumbrance_certificate','patta_chitta',
                                           'salary_slip','bank_statement','agreement','seizure_notice',
                                           'seizure_photo','expense_receipt','other');


-- =====================================================================
--  SECTION 2 — GLOBAL TABLES
-- =====================================================================

-- ---------------------------------------------------------------------
-- 2.1 financiers — one row per company. ALSO the login account,
--     since there is exactly one shared login per company.
-- ---------------------------------------------------------------------
CREATE TABLE financiers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_code          VARCHAR(20)  UNIQUE NOT NULL,      -- FIN-000123

    -- Business details
    company_name            VARCHAR(150) NOT NULL,
    entity_type             entity_type_enum NOT NULL,
    business_pan            VARCHAR(10),                       -- OPTIONAL (changed in v2)
    gstin                   VARCHAR(15),
    established_year        SMALLINT,

    -- Money lending licence (optional)
    licence_number          VARCHAR(60),
    licence_authority       VARCHAR(150),
    licence_valid_until     DATE,

    -- Business address
    address_line1           VARCHAR(200) NOT NULL,
    address_line2           VARCHAR(200),
    area                    VARCHAR(100) NOT NULL,
    city                    VARCHAR(100) NOT NULL,
    taluk                   VARCHAR(100),
    district                VARCHAR(100) NOT NULL,
    state                   VARCHAR(100) NOT NULL,
    pincode                 CHAR(6)      NOT NULL,

    -- Owner / primary contact
    owner_name              VARCHAR(100) NOT NULL,
    owner_pan               VARCHAR(10),                       -- OPTIONAL (changed in v2)
    primary_mobile          VARCHAR(10)  UNIQUE NOT NULL,      -- the login identity
    alternate_mobile        VARCHAR(10),
    email                   VARCHAR(150),

    -- Shared login credentials (no users table in v2)
    mpin_hash               TEXT,
    biometric_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    last_otp_verified_at    TIMESTAMPTZ,
    failed_login_count      SMALLINT NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,

    -- Platform
    status                  financier_status_enum NOT NULL DEFAULT 'pending_verification',
    subscription_tier       VARCHAR(30) DEFAULT 'basic',
    subscription_valid_until DATE,
    verified_at             TIMESTAMPTZ,

    -- Shared registry participation
    registry_enrolled       BOOLEAN NOT NULL DEFAULT TRUE,
    registry_query_limit    INT     NOT NULL DEFAULT 100,      -- per day
    registry_suspended      BOOLEAN NOT NULL DEFAULT FALSE,
    registry_suspend_reason TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_fin_pan       CHECK (business_pan IS NULL OR business_pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    CONSTRAINT ck_fin_owner_pan CHECK (owner_pan   IS NULL OR owner_pan   ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    CONSTRAINT ck_fin_mobile    CHECK (primary_mobile ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_fin_pincode   CHECK (pincode ~ '^[1-9][0-9]{5}$')
);

-- PAN optional, but must still be unique when supplied
CREATE UNIQUE INDEX uq_fin_business_pan ON financiers(business_pan) WHERE business_pan IS NOT NULL;
CREATE INDEX idx_financiers_district ON financiers(district) WHERE status = 'active';
CREATE INDEX idx_financiers_status   ON financiers(status);


-- ---------------------------------------------------------------------
-- 2.2 otp_requests — audit only. Live OTPs live in Redis with a TTL.
-- ---------------------------------------------------------------------
CREATE TABLE otp_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile                  VARCHAR(10) NOT NULL,
    financier_id            UUID REFERENCES financiers(id),
    purpose                 VARCHAR(40) NOT NULL,        -- login | registration | mobile_change
    otp_hash                TEXT        NOT NULL,        -- argon2. NEVER plaintext.
    session_token           UUID        NOT NULL,        -- binds the OTP to its originating session
    expires_at              TIMESTAMPTZ NOT NULL,
    verified_at             TIMESTAMPTZ,
    attempt_count           SMALLINT NOT NULL DEFAULT 0,
    resend_count            SMALLINT NOT NULL DEFAULT 0,
    delivery_status         VARCHAR(20) NOT NULL DEFAULT 'queued',
    ip_address              INET,
    device_fingerprint      TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_mobile_time ON otp_requests(mobile, created_at DESC);
CREATE INDEX idx_otp_session     ON otp_requests(session_token);


-- ---------------------------------------------------------------------
-- 2.3 sessions — refresh token families with rotation + reuse detection
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    family_id               UUID NOT NULL,
    refresh_token_hash      TEXT NOT NULL,
    device_id               VARCHAR(100),
    device_name             VARCHAR(100),
    device_platform         VARCHAR(20),                 -- android | ios | web
    ip_address              INET,
    is_trusted_device       BOOLEAN NOT NULL DEFAULT FALSE,
    issued_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    last_used_at            TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    revoked_reason          VARCHAR(60)                  -- logout | rotation | reuse_detected | admin
);
CREATE INDEX idx_sessions_financier ON sessions(financier_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_sessions_family    ON sessions(family_id);


-- ---------------------------------------------------------------------
-- 2.4 vehicle_pledge_registry  ⭐ THE SHARED CROSS-COMPANY TABLE
--     No borrower PII. No loan financials.
-- ---------------------------------------------------------------------
CREATE TABLE vehicle_pledge_registry (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vehicle_number_norm     VARCHAR(20) NOT NULL,        -- primary lookup key
    vehicle_number_raw      VARCHAR(30) NOT NULL,
    chassis_number_norm     VARCHAR(25),                 -- survives re-registration
    engine_number_norm      VARCHAR(25),

    vehicle_type            vehicle_type_enum,
    make                    VARCHAR(50),
    model                   VARCHAR(50),
    manufacturing_year      SMALLINT,

    financier_id            UUID NOT NULL REFERENCES financiers(id),
    loan_id                 UUID NOT NULL,               -- internal only, NEVER returned

    status                  pledge_status_enum NOT NULL DEFAULT 'active',
    pledged_on              DATE NOT NULL,
    released_on             DATE,
    source                  VARCHAR(20) NOT NULL DEFAULT 'auto',   -- auto | manual | migrated

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_vpr_norm     CHECK (vehicle_number_norm ~ '^[A-Z0-9]{6,20}$'),
    CONSTRAINT ck_vpr_released CHECK (
        (status = 'closed' AND released_on IS NOT NULL) OR
        (status = 'active' AND released_on IS NULL))
);

CREATE UNIQUE INDEX uq_vpr_active_per_financier
    ON vehicle_pledge_registry(vehicle_number_norm, financier_id) WHERE status = 'active';
CREATE INDEX idx_vpr_lookup_active
    ON vehicle_pledge_registry(vehicle_number_norm) WHERE status = 'active';
CREATE INDEX idx_vpr_chassis_active
    ON vehicle_pledge_registry(chassis_number_norm) WHERE status = 'active' AND chassis_number_norm IS NOT NULL;
CREATE INDEX idx_vpr_engine_active
    ON vehicle_pledge_registry(engine_number_norm)  WHERE status = 'active' AND engine_number_norm IS NOT NULL;
CREATE INDEX idx_vpr_financier ON vehicle_pledge_registry(financier_id, status);
CREATE INDEX idx_vpr_stale     ON vehicle_pledge_registry(pledged_on) WHERE status = 'active';


-- ---------------------------------------------------------------------
-- 2.5 vehicle_inspection_queries — audit of every lookup.
--     Powers rate limiting, enumeration detection, reciprocity scoring.
-- ---------------------------------------------------------------------
CREATE TABLE vehicle_inspection_queries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id),

    queried_vehicle_raw     VARCHAR(30) NOT NULL,
    queried_vehicle_norm    VARCHAR(20) NOT NULL,
    queried_chassis_norm    VARCHAR(25),

    result_found            BOOLEAN NOT NULL,
    matched_registry_id     UUID REFERENCES vehicle_pledge_registry(id),
    matched_on              VARCHAR(20),                 -- vehicle_number | chassis | engine

    query_context           VARCHAR(30) NOT NULL,        -- loan_creation | standalone
    linked_loan_id          UUID,
    stated_reason           TEXT,

    overridden              BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason         TEXT,

    ip_address              INET,
    device_id               VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_viq_financier_time ON vehicle_inspection_queries(financier_id, created_at DESC);
CREATE INDEX idx_viq_vehicle        ON vehicle_inspection_queries(queried_vehicle_norm);


-- ---------------------------------------------------------------------
-- 2.6 gold_rates — shared daily reference rate
-- ---------------------------------------------------------------------
CREATE TABLE gold_rates (
    rate_date               DATE PRIMARY KEY,
    rate_24k_per_gram       NUMERIC(10,2) NOT NULL CHECK (rate_24k_per_gram > 0),
    rate_22k_per_gram       NUMERIC(10,2),
    source                  VARCHAR(50),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------
-- 2.7 audit_log — append-only. No user_id: one shared login per company,
--     so this records WHAT changed and WHEN, not who.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id                      BIGSERIAL,
    financier_id            UUID,
    action                  VARCHAR(80) NOT NULL,
    entity_type             VARCHAR(50),
    entity_id               UUID,
    old_value               JSONB,
    new_value               JSONB,
    reason                  TEXT,
    entered_by_name         VARCHAR(100),                -- optional free text, if captured
    ip_address              INET,
    device_id               VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_log_2026_09 PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

CREATE INDEX idx_audit_financier ON audit_log(financier_id, created_at DESC);
CREATE INDEX idx_audit_entity    ON audit_log(entity_type, entity_id);

-- Append-only: the app role gets SELECT and INSERT, never UPDATE or DELETE.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM PUBLIC;


-- =====================================================================
--  SECTION 3 — TENANT TABLES
-- =====================================================================

-- ---------------------------------------------------------------------
-- 3.1 brokers  (commission fields removed in v2)
-- ---------------------------------------------------------------------
CREATE TABLE brokers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    broker_code             VARCHAR(20) NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    phone                   VARCHAR(10)  NOT NULL,
    alternate_phone         VARCHAR(10),
    address                 TEXT         NOT NULL,
    area                    VARCHAR(100),
    district                VARCHAR(100),
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_broker_phone CHECK (phone ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT uq_broker_code  UNIQUE (financier_id, broker_code),
    CONSTRAINT uq_broker_phone UNIQUE (financier_id, phone)
);
CREATE INDEX idx_brokers_name ON brokers USING gin (name gin_trgm_ops);


-- ---------------------------------------------------------------------
-- 3.2 customers
--     NO Aadhaar number column. The Aadhaar photocopy is stored in
--     documents with category = 'aadhaar'.
--     Dedup keys: PAN and mobile only.
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    customer_code           VARCHAR(20) NOT NULL,            -- CUS-2026-00123

    full_name               VARCHAR(100) NOT NULL,
    father_spouse_name      VARCHAR(100),
    date_of_birth           DATE,
    gender                  VARCHAR(10),
    mobile                  VARCHAR(10) NOT NULL,            -- dedup key. No OTP verification in v2.
    alternate_mobile        VARCHAR(10),
    email                   VARCHAR(150),
    preferred_language      VARCHAR(10) NOT NULL DEFAULT 'ta',

    address_line1           VARCHAR(200) NOT NULL,
    address_line2           VARCHAR(200),
    area                    VARCHAR(100) NOT NULL,
    city_village            VARCHAR(100) NOT NULL,
    taluk                   VARCHAR(100),
    district                VARCHAR(100) NOT NULL,
    state                   VARCHAR(100) NOT NULL,
    pincode                 CHAR(6)      NOT NULL,
    latitude                NUMERIC(10,7),
    longitude               NUMERIC(10,7),

    -- PAN is the primary dedup key. Encrypted at rest; pan_hash for lookup.
    pan_encrypted           BYTEA NOT NULL,
    pan_last4               CHAR(4) NOT NULL,
    pan_hash                TEXT NOT NULL,                   -- salted SHA-256, dedup + search

    -- Address proof (gas / electricity bill); the file itself is in documents
    address_proof_type      VARCHAR(30),                     -- gas_bill | electricity_bill
    address_proof_number    VARCHAR(50),
    address_proof_date      DATE,

    occupation              VARCHAR(30),
    monthly_income          NUMERIC(15,2),

    broker_id               UUID REFERENCES brokers(id),

    is_blacklisted          BOOLEAN NOT NULL DEFAULT FALSE,
    blacklist_reason        TEXT,
    blacklisted_at          TIMESTAMPTZ,

    -- DPDP consent
    consent_given_at        TIMESTAMPTZ,
    registry_consent        BOOLEAN NOT NULL DEFAULT FALSE,  -- separate tick for shared registry

    -- No delete, ever. Inactive only hides from default lists.
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_cust_mobile  CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_cust_pincode CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    CONSTRAINT ck_cust_age     CHECK (date_of_birth IS NULL
                                      OR date_of_birth <= CURRENT_DATE - INTERVAL '18 years'),
    CONSTRAINT uq_cust_code    UNIQUE (financier_id, customer_code)
);

-- Deduplication: PAN and mobile, hard uniqueness per company.
-- NOT partial on is_active: a deactivated customer still blocks reuse,
-- because the record is never deleted and must stay findable.
CREATE UNIQUE INDEX uq_cust_pan    ON customers(financier_id, pan_hash);
CREATE UNIQUE INDEX uq_cust_mobile ON customers(financier_id, mobile);
CREATE INDEX idx_cust_name_trgm ON customers USING gin (full_name gin_trgm_ops);
CREATE INDEX idx_cust_financier ON customers(financier_id) WHERE is_active;
CREATE INDEX idx_cust_broker    ON customers(broker_id);

-- Belt and braces: customer records are permanent.
REVOKE DELETE, TRUNCATE ON customers FROM PUBLIC;


-- ---------------------------------------------------------------------
-- 3.3 documents — unified vault (Aadhaar photocopy lives here)
-- ---------------------------------------------------------------------
CREATE TABLE documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,

    customer_id             UUID REFERENCES customers(id),
    loan_id                 UUID,                            -- FK added after loans
    seizure_id              UUID,
    expense_id              UUID,

    category                doc_category_enum NOT NULL,
    file_key                TEXT NOT NULL,                   -- S3 object key
    file_name               VARCHAR(255),
    mime_type               VARCHAR(100),
    file_size_bytes         BIGINT,
    checksum_sha256         TEXT,

    document_number         VARCHAR(100),
    issue_date              DATE,
    expiry_date             DATE,                            -- feeds the Expiration Report
    issuing_authority       VARCHAR(150),

    original_held           BOOLEAN NOT NULL DEFAULT FALSE,
    custody_location        VARCHAR(150),

    version                 SMALLINT NOT NULL DEFAULT 1,
    supersedes_id           UUID REFERENCES documents(id),   -- re-upload never overwrites

    uploaded_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_customer ON documents(customer_id, category);
CREATE INDEX idx_doc_loan     ON documents(loan_id, category);
CREATE INDEX idx_doc_expiry   ON documents(financier_id, expiry_date) WHERE expiry_date IS NOT NULL;


-- ---------------------------------------------------------------------
-- 3.4 loans — no approval fields; starts ACTIVE
-- ---------------------------------------------------------------------
CREATE TABLE loans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    loan_number             VARCHAR(25) NOT NULL,            -- LN-VEH-2026-00123
    customer_id             UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    guarantor_customer_id   UUID REFERENCES customers(id),
    broker_id               UUID REFERENCES brokers(id),

    loan_type               loan_type_enum NOT NULL,
    status                  loan_status_enum NOT NULL DEFAULT 'active',

    -- Terms
    principal_amount        NUMERIC(15,2) NOT NULL CHECK (principal_amount > 0),
    interest_rate           NUMERIC(6,3)  NOT NULL CHECK (interest_rate > 0),
    rate_type               rate_type_enum NOT NULL DEFAULT 'per_month',
    interest_scheme         interest_scheme_enum NOT NULL DEFAULT 'A_monthly_interest_bullet',
    tenure_value            INT NOT NULL CHECK (tenure_value > 0),
    tenure_unit             VARCHAR(10) NOT NULL DEFAULT 'months',   -- months | days
    repayment_frequency     frequency_enum NOT NULL DEFAULT 'monthly',

    -- Charges
    processing_fee          NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_deductions        NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_disbursal_amount    NUMERIC(15,2),
    foreclosure_charge_pct  NUMERIC(6,3) DEFAULT 0,

    -- Overdue policy: percentage OR fixed amount (v2 change)
    grace_days              SMALLINT NOT NULL DEFAULT 5,
    penal_type              penal_type_enum NOT NULL DEFAULT 'percentage',
    penal_percent           NUMERIC(6,3),                    -- % per month on overdue amount
    penal_fixed_amount      NUMERIC(15,2),                   -- flat ₹ per overdue installment
    penal_repeats_monthly   BOOLEAN NOT NULL DEFAULT FALSE,  -- only meaningful for fixed_amount

    collateral_valuation    NUMERIC(15,2),

    -- Dates
    loan_date               DATE NOT NULL DEFAULT CURRENT_DATE,
    disbursal_date          DATE NOT NULL DEFAULT CURRENT_DATE,
    disbursal_mode          payment_mode_enum,
    disbursal_reference     VARCHAR(100),
    first_due_date          DATE,
    maturity_date           DATE,
    closed_at               TIMESTAMPTZ,

    -- Derived balances (source of truth is loan_transactions)
    total_interest_payable  NUMERIC(15,2) DEFAULT 0,
    total_repayable         NUMERIC(15,2) DEFAULT 0,
    principal_paid          NUMERIC(15,2) NOT NULL DEFAULT 0,
    interest_paid           NUMERIC(15,2) NOT NULL DEFAULT 0,
    penal_paid              NUMERIC(15,2) NOT NULL DEFAULT 0,
    outstanding_principal   NUMERIC(15,2) NOT NULL DEFAULT 0,
    outstanding_interest    NUMERIC(15,2) NOT NULL DEFAULT 0,
    outstanding_penal       NUMERIC(15,2) NOT NULL DEFAULT 0,
    last_payment_date       DATE,

    -- Vehicle inspection gate
    inspection_query_id     UUID REFERENCES vehicle_inspection_queries(id),
    inspection_result       VARCHAR(20),                     -- clear | hit | unavailable
    inspection_overridden   BOOLEAN NOT NULL DEFAULT FALSE,
    inspection_override_reason TEXT,

    remarks                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_loan_number UNIQUE (financier_id, loan_number),
    -- Exactly one penal configuration must be populated
    CONSTRAINT ck_loan_penal CHECK (
        (penal_type = 'percentage'   AND penal_percent      IS NOT NULL AND penal_percent      >= 0) OR
        (penal_type = 'fixed_amount' AND penal_fixed_amount IS NOT NULL AND penal_fixed_amount >= 0) OR
        (penal_type = 'none')
    )
);

CREATE INDEX idx_loans_customer ON loans(customer_id);
CREATE INDEX idx_loans_status   ON loans(financier_id, status);
CREATE INDEX idx_loans_type     ON loans(financier_id, loan_type, status);
CREATE INDEX idx_loans_maturity ON loans(financier_id, maturity_date) WHERE status = 'active';

ALTER TABLE documents ADD CONSTRAINT fk_doc_loan
    FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------
-- 3.5 loan_personal_details
--     Removed in v2: employee_id, department, office_address,
--     date_of_joining, retirement_date.
--     Reference 2 and verification statuses now optional.
-- ---------------------------------------------------------------------
CREATE TABLE loan_personal_details (
    loan_id                 UUID PRIMARY KEY REFERENCES loans(id) ON DELETE CASCADE,
    financier_id            UUID NOT NULL REFERENCES financiers(id),

    employer_name           VARCHAR(150) NOT NULL,
    designation             VARCHAR(100),
    net_monthly_salary      NUMERIC(15,2) NOT NULL CHECK (net_monthly_salary > 0),
    salary_credit_day       SMALLINT CHECK (salary_credit_day BETWEEN 1 AND 31),

    -- Salary account.  NEVER store full card number, CVV or PIN.
    bank_name               VARCHAR(100) NOT NULL,
    bank_branch             VARCHAR(100),
    ifsc_code               VARCHAR(11),
    account_number_encrypted BYTEA,
    account_number_last4    CHAR(4),
    card_last4              CHAR(4),
    card_physically_held    BOOLEAN NOT NULL DEFAULT FALSE,
    card_custody_location   VARCHAR(150),

    -- Reference 1 mandatory; reference 2 optional
    ref1_name               VARCHAR(100) NOT NULL,
    ref1_phone              VARCHAR(10)  NOT NULL,
    ref1_relationship       VARCHAR(50),
    ref1_verified           BOOLEAN,                         -- optional (NULL = not recorded)
    ref2_name               VARCHAR(100),
    ref2_phone              VARCHAR(10),
    ref2_relationship       VARCHAR(50),
    ref2_verified           BOOLEAN,

    CONSTRAINT ck_lpd_ifsc CHECK (ifsc_code IS NULL OR ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    CONSTRAINT ck_lpd_ref1 CHECK (ref1_phone ~ '^[6-9][0-9]{9}$')
);


-- ---------------------------------------------------------------------
-- 3.6 loan_vehicle_details  (duplicate_key_held removed in v2)
-- ---------------------------------------------------------------------
CREATE TABLE loan_vehicle_details (
    loan_id                 UUID PRIMARY KEY REFERENCES loans(id) ON DELETE CASCADE,
    financier_id            UUID NOT NULL REFERENCES financiers(id),

    vehicle_number_raw      VARCHAR(30) NOT NULL,
    vehicle_number_norm     VARCHAR(20) NOT NULL,
    chassis_number          VARCHAR(25),
    chassis_number_norm     VARCHAR(25),
    engine_number           VARCHAR(25),
    engine_number_norm      VARCHAR(25),

    vehicle_type            vehicle_type_enum NOT NULL,
    is_commercial           BOOLEAN NOT NULL DEFAULT FALSE,
    make                    VARCHAR(50) NOT NULL,
    model                   VARCHAR(50) NOT NULL,
    variant                 VARCHAR(50),
    manufacturing_year      SMALLINT NOT NULL,
    fuel_type               VARCHAR(20),
    colour                  VARCHAR(30),
    odometer_reading        INT,

    rc_number               VARCHAR(30),
    rc_owner_name           VARCHAR(100) NOT NULL,
    rc_registration_date    DATE,
    rc_original_held        BOOLEAN NOT NULL DEFAULT FALSE,

    -- → Expiration Report
    insurance_company       VARCHAR(100) NOT NULL,
    insurance_policy_number VARCHAR(60)  NOT NULL,
    insurance_expiry        DATE         NOT NULL,
    licence_number          VARCHAR(30)  NOT NULL,
    licence_expiry          DATE,
    permit_number           VARCHAR(50),
    permit_expiry           DATE,
    fitness_expiry          DATE,

    vehicle_valuation       NUMERIC(15,2) NOT NULL,

    CONSTRAINT ck_lvd_commercial_permit CHECK (is_commercial = FALSE OR permit_expiry IS NOT NULL),
    CONSTRAINT ck_lvd_vnum CHECK (vehicle_number_norm ~ '^[A-Z0-9]{6,20}$')
);

-- NOT unique: the same vehicle can back a new loan after the old one closes.
-- Active-pledge uniqueness is enforced by uq_vpr_active_per_financier.
CREATE INDEX idx_lvd_vehicle       ON loan_vehicle_details(financier_id, vehicle_number_norm);
CREATE INDEX idx_lvd_ins_expiry    ON loan_vehicle_details(financier_id, insurance_expiry);
CREATE INDEX idx_lvd_permit_expiry ON loan_vehicle_details(financier_id, permit_expiry) WHERE permit_expiry IS NOT NULL;


-- ---------------------------------------------------------------------
-- 3.7 loan_gold_details + loan_gold_items  (simplified in v2)
--     Removed: net weight, deduction, purity test method, max LTV,
--              appraiser, packet seal number, storage location,
--              margin monitoring.
-- ---------------------------------------------------------------------
CREATE TABLE loan_gold_details (
    loan_id                 UUID PRIMARY KEY REFERENCES loans(id) ON DELETE CASCADE,
    financier_id            UUID NOT NULL REFERENCES financiers(id),

    total_weight_g          NUMERIC(10,3) NOT NULL CHECK (total_weight_g > 0),
    weighted_avg_purity     NUMERIC(5,2),                    -- karat
    item_count              SMALLINT NOT NULL DEFAULT 0,

    gold_rate_per_gram_24k  NUMERIC(10,2) NOT NULL CHECK (gold_rate_per_gram_24k > 0),
    rate_date               DATE NOT NULL,
    rate_manually_overridden BOOLEAN NOT NULL DEFAULT FALSE,

    computed_valuation      NUMERIC(15,2) NOT NULL,          -- system-calculated
    entered_valuation       NUMERIC(15,2) NOT NULL,          -- user-entered
    valuation_variance_pct  NUMERIC(6,3)
);

CREATE TABLE loan_gold_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                 UUID NOT NULL REFERENCES loan_gold_details(loan_id) ON DELETE CASCADE,
    financier_id            UUID NOT NULL REFERENCES financiers(id),
    item_seq                SMALLINT NOT NULL,

    item_type               VARCHAR(40) NOT NULL,            -- chain | bangle | ring | necklace | coin | other
    quantity                SMALLINT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    weight_g                NUMERIC(10,3) NOT NULL CHECK (weight_g > 0),   -- single gross weight
    purity                  gold_purity_enum NOT NULL,
    hallmark_huid           VARCHAR(30),
    item_valuation          NUMERIC(15,2),
    description             TEXT,
    is_released             BOOLEAN NOT NULL DEFAULT FALSE,
    released_at             TIMESTAMPTZ,

    CONSTRAINT uq_lgi_seq UNIQUE (loan_id, item_seq)
);
CREATE INDEX idx_lgi_loan ON loan_gold_items(loan_id);


-- ---------------------------------------------------------------------
-- 3.8 loan_document_details (land / property)
-- ---------------------------------------------------------------------
CREATE TABLE loan_document_details (
    loan_id                 UUID PRIMARY KEY REFERENCES loans(id) ON DELETE CASCADE,
    financier_id            UUID NOT NULL REFERENCES financiers(id),

    -- Uniqueness key is SRO + year + document number, not the number alone
    document_number         VARCHAR(60) NOT NULL,
    sro_office              VARCHAR(100) NOT NULL,
    registration_year       SMALLINT NOT NULL,
    document_type           VARCHAR(40),

    -- Secondary key: the land itself
    survey_number           VARCHAR(40) NOT NULL,
    subdivision_number      VARCHAR(20),
    patta_number            VARCHAR(40),
    village                 VARCHAR(100) NOT NULL,
    taluk                   VARCHAR(100) NOT NULL,
    district                VARCHAR(100) NOT NULL,
    land_parcel_key         VARCHAR(200),                    -- normalised composite, built by the app

    extent_value            NUMERIC(12,4) NOT NULL CHECK (extent_value > 0),
    extent_unit             VARCHAR(15) NOT NULL,            -- acres | cents | sqft
    land_classification     VARCHAR(30),

    current_price_per_unit  NUMERIC(15,2) NOT NULL CHECK (current_price_per_unit > 0),
    guideline_value         NUMERIC(15,2),
    total_valuation         NUMERIC(15,2) NOT NULL,

    title_holder_count      SMALLINT DEFAULT 1,

    ec_number               VARCHAR(60),
    ec_obtained_on          DATE,
    ec_valid_until          DATE,                            -- → Expiration Report
    ec_findings             VARCHAR(30),                     -- clear | encumbrance_found

    originals_held_list     JSONB,                           -- itemised list of papers actually held

    CONSTRAINT uq_ldd_docnum UNIQUE (financier_id, sro_office, registration_year, document_number, loan_id)
);
CREATE INDEX idx_ldd_parcel    ON loan_document_details(financier_id, land_parcel_key);
CREATE INDEX idx_ldd_ec_expiry ON loan_document_details(financier_id, ec_valid_until) WHERE ec_valid_until IS NOT NULL;


-- ---------------------------------------------------------------------
-- 3.9 loan_schedule — one row per installment
-- ---------------------------------------------------------------------
CREATE TABLE loan_schedule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    loan_id                 UUID NOT NULL REFERENCES loans(id) ON DELETE CASCADE,

    installment_no          SMALLINT NOT NULL,
    due_date                DATE NOT NULL,

    due_principal           NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_interest            NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_total               NUMERIC(15,2) NOT NULL,
    opening_balance         NUMERIC(15,2),
    closing_balance         NUMERIC(15,2),

    paid_principal          NUMERIC(15,2) NOT NULL DEFAULT 0,
    paid_interest           NUMERIC(15,2) NOT NULL DEFAULT 0,
    paid_penal              NUMERIC(15,2) NOT NULL DEFAULT 0,
    paid_total              NUMERIC(15,2) NOT NULL DEFAULT 0,
    last_paid_date          DATE,

    penal_accrued           NUMERIC(15,2) NOT NULL DEFAULT 0,
    waived_amount           NUMERIC(15,2) NOT NULL DEFAULT 0,

    status                  schedule_status_enum NOT NULL DEFAULT 'pending',
    days_past_due           INT NOT NULL DEFAULT 0,          -- recomputed nightly

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_sched UNIQUE (loan_id, installment_no)
);

-- Drives Current Dues and Pending Dues
CREATE INDEX idx_sched_due     ON loan_schedule(financier_id, due_date)
    WHERE status IN ('pending','due','partially_paid','overdue');
CREATE INDEX idx_sched_overdue ON loan_schedule(financier_id, days_past_due DESC)
    WHERE status = 'overdue';
CREATE INDEX idx_sched_loan    ON loan_schedule(loan_id, installment_no);


-- ---------------------------------------------------------------------
-- 3.10 loan_transactions — the immutable ledger.
--      v2: reference_number, GPS, device and collected_by removed.
--      entered_by_name is optional free text.
-- ---------------------------------------------------------------------
CREATE TABLE loan_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    loan_id                 UUID NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    schedule_id             UUID REFERENCES loan_schedule(id),

    txn_type                txn_type_enum NOT NULL,
    txn_date                DATE NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL CHECK (amount > 0),

    -- Appropriation split
    principal_component     NUMERIC(15,2) NOT NULL DEFAULT 0,
    interest_component      NUMERIC(15,2) NOT NULL DEFAULT 0,
    penal_component         NUMERIC(15,2) NOT NULL DEFAULT 0,
    charge_component        NUMERIC(15,2) NOT NULL DEFAULT 0,
    excess_component        NUMERIC(15,2) NOT NULL DEFAULT 0,

    balance_after           NUMERIC(15,2),

    payment_mode            payment_mode_enum,
    receipt_number          VARCHAR(30),                     -- sequential per company, gapless
    entered_by_name         VARCHAR(100),                    -- optional free text

    -- Reversal (corrections are contra entries, never edits)
    is_reversed             BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_by_txn_id      UUID REFERENCES loan_transactions(id),
    reverses_txn_id         UUID REFERENCES loan_transactions(id),
    reversal_reason         TEXT,

    remarks                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_txn_split CHECK (
        txn_type <> 'repayment' OR
        amount = principal_component + interest_component + penal_component
                 + charge_component + excess_component),
    CONSTRAINT uq_receipt UNIQUE (financier_id, receipt_number)
);

CREATE INDEX idx_txn_loan ON loan_transactions(loan_id, txn_date DESC);
CREATE INDEX idx_txn_date ON loan_transactions(financier_id, txn_date DESC);

-- Ledger is immutable
CREATE RULE txn_no_delete AS ON DELETE TO loan_transactions DO INSTEAD NOTHING;
REVOKE DELETE, TRUNCATE ON loan_transactions FROM PUBLIC;


-- ---------------------------------------------------------------------
-- 3.11 seizures — simplified in v2.
--      Removed: status enum, authorised_by, seized_by, agency_contact,
--      seizure location, inventory_list, storage_location,
--      storage_cost_per_day. State is derived from which dates are set.
--      loan_id is kept as an internal link (not a user-entered field) —
--      without it the seizure can't pull the outstanding amount.
-- ---------------------------------------------------------------------
CREATE TABLE seizures (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    seizure_number          VARCHAR(25) NOT NULL,            -- SZ-2026-0012
    loan_id                 UUID NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    customer_id             UUID NOT NULL REFERENCES customers(id),

    collateral_type         loan_type_enum NOT NULL,
    collateral_reference    VARCHAR(100) NOT NULL,           -- vehicle no / doc no / item description

    seizure_date            DATE NOT NULL,
    reason                  seizure_reason_enum NOT NULL,
    reason_detail           TEXT,

    outstanding_at_seizure  NUMERIC(15,2) NOT NULL,
    days_overdue_at_seizure INT,

    notice_served           BOOLEAN NOT NULL DEFAULT FALSE,
    notice_date             DATE,
    notice_mode             VARCHAR(40),                     -- registered_post | hand | courier

    agency_name             VARCHAR(150),

    -- Valuation & disposal
    current_valuation       NUMERIC(15,2),
    valuation_date          DATE,
    sale_date               DATE,
    sale_mode               VARCHAR(30),                     -- private_sale | auction
    sale_price              NUMERIC(15,2),
    buyer_name              VARCHAR(150),
    recovery_charges        NUMERIC(15,2) NOT NULL DEFAULT 0,
    surplus_amount          NUMERIC(15,2),
    shortfall_amount        NUMERIC(15,2),
    surplus_refunded        BOOLEAN NOT NULL DEFAULT FALSE,
    surplus_refund_date     DATE,
    surplus_refund_ref      VARCHAR(100),

    -- Release back to the borrower
    release_date            DATE,
    released_to             VARCHAR(150),

    remarks                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_seizure_number UNIQUE (financier_id, seizure_number),
    CONSTRAINT ck_sz_not_both    CHECK (NOT (release_date IS NOT NULL AND sale_date IS NOT NULL))
);
CREATE INDEX idx_seizure_loan ON seizures(loan_id);
CREATE INDEX idx_seizure_date ON seizures(financier_id, seizure_date DESC);

ALTER TABLE documents ADD CONSTRAINT fk_doc_seizure
    FOREIGN KEY (seizure_id) REFERENCES seizures(id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------
-- 3.12 Business expense
-- ---------------------------------------------------------------------
CREATE TABLE expense_categories (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID REFERENCES financiers(id) ON DELETE CASCADE,  -- NULL = system default
    name                    VARCHAR(80) NOT NULL,
    kind                    expense_kind_enum NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_exp_cat UNIQUE (financier_id, name, kind)
);
-- NULLs are not equal in a UNIQUE constraint, so system defaults need their own index
CREATE UNIQUE INDEX uq_exp_cat_system ON expense_categories(name, kind) WHERE financier_id IS NULL;

CREATE TABLE running_costs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    cost_code               VARCHAR(25) NOT NULL,            -- RC-2026-0007
    category_id             UUID NOT NULL REFERENCES expense_categories(id),
    description             VARCHAR(200) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL CHECK (amount > 0),

    frequency               frequency_enum NOT NULL,
    start_date              DATE NOT NULL,
    end_date                DATE,
    due_day_of_period       SMALLINT NOT NULL CHECK (due_day_of_period BETWEEN 1 AND 31),

    payee_name              VARCHAR(150) NOT NULL,
    payee_contact           VARCHAR(15),
    payment_mode            payment_mode_enum NOT NULL,
    employee_name           VARCHAR(100),                    -- free text, for salary entries

    auto_generate           BOOLEAN NOT NULL DEFAULT TRUE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    notes                   TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_rc_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE UNIQUE INDEX uq_running_cost_code ON running_costs(financier_id, cost_code);
CREATE INDEX idx_rc_active ON running_costs(financier_id) WHERE is_active;

CREATE TABLE running_cost_payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    running_cost_id         UUID NOT NULL REFERENCES running_costs(id) ON DELETE CASCADE,
    period_label            VARCHAR(20) NOT NULL,            -- 2026-09
    due_date                DATE NOT NULL,
    amount_due              NUMERIC(15,2) NOT NULL,
    amount_paid             NUMERIC(15,2) NOT NULL DEFAULT 0,
    status                  payment_status_enum NOT NULL DEFAULT 'pending',
    paid_date               DATE,
    payment_mode            payment_mode_enum,
    receipt_document_id     UUID REFERENCES documents(id),
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rcp_period UNIQUE (running_cost_id, period_label)
);
CREATE INDEX idx_rcp_due ON running_cost_payments(financier_id, due_date) WHERE status <> 'paid';

CREATE TABLE petty_expenses (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    expense_code            VARCHAR(25) NOT NULL,            -- PE-2026-00451
    expense_date            DATE NOT NULL DEFAULT CURRENT_DATE,
    category_id             UUID NOT NULL REFERENCES expense_categories(id),
    description             VARCHAR(200) NOT NULL CHECK (length(description) >= 5),
    amount                  NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    payment_mode            payment_mode_enum NOT NULL,
    paid_to                 VARCHAR(150),

    receipt_document_id     UUID REFERENCES documents(id),

    linked_loan_id          UUID REFERENCES loans(id),
    linked_seizure_id       UUID REFERENCES seizures(id),
    is_recoverable          BOOLEAN NOT NULL DEFAULT FALSE,

    entered_by_name         VARCHAR(100),                    -- optional free text

    is_reversed             BOOLEAN NOT NULL DEFAULT FALSE,
    reversal_reason         TEXT,

    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pe_code UNIQUE (financier_id, expense_code)
);
CREATE INDEX idx_pe_date    ON petty_expenses(financier_id, expense_date DESC);
CREATE INDEX idx_pe_seizure ON petty_expenses(linked_seizure_id) WHERE linked_seizure_id IS NOT NULL;

ALTER TABLE documents ADD CONSTRAINT fk_doc_expense
    FOREIGN KEY (expense_id) REFERENCES petty_expenses(id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------
-- 3.13 financier_settings — per-company configuration.
--      Notification and approval settings removed in v2.
-- ---------------------------------------------------------------------
CREATE TABLE financier_settings (
    financier_id            UUID PRIMARY KEY REFERENCES financiers(id) ON DELETE CASCADE,

    default_interest_scheme interest_scheme_enum NOT NULL DEFAULT 'A_monthly_interest_bullet',
    default_rate_type       rate_type_enum NOT NULL DEFAULT 'per_month',
    default_grace_days      SMALLINT NOT NULL DEFAULT 5,
    default_penal_type      penal_type_enum NOT NULL DEFAULT 'percentage',
    default_penal_percent   NUMERIC(6,3) DEFAULT 2.000,
    default_penal_fixed     NUMERIC(15,2) DEFAULT 0,

    interest_rate_ceiling   NUMERIC(6,3),                    -- statutory cap, warning only
    appropriation_order     JSONB NOT NULL
                              DEFAULT '["penal","charges","interest","principal"]',

    petty_expense_receipt_prompt NUMERIC(15,2) NOT NULL DEFAULT 500,

    require_otp_every_login BOOLEAN NOT NULL DEFAULT TRUE,
    trusted_device_days     SMALLINT NOT NULL DEFAULT 30,

    print_header_text       VARCHAR(200),                    -- printed on receipts and report headers
    print_footer_text       VARCHAR(200),

    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------
-- 3.14 daily_snapshots — precomputed dashboard aggregates
-- ---------------------------------------------------------------------
CREATE TABLE daily_snapshots (
    financier_id            UUID NOT NULL REFERENCES financiers(id) ON DELETE CASCADE,
    snapshot_date           DATE NOT NULL,

    active_loan_count       INT NOT NULL DEFAULT 0,
    total_disbursed         NUMERIC(18,2) NOT NULL DEFAULT 0,
    outstanding_principal   NUMERIC(18,2) NOT NULL DEFAULT 0,
    outstanding_interest    NUMERIC(18,2) NOT NULL DEFAULT 0,
    collected_today         NUMERIC(18,2) NOT NULL DEFAULT 0,

    overdue_count           INT NOT NULL DEFAULT 0,
    overdue_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    par_1_30                NUMERIC(18,2) NOT NULL DEFAULT 0,
    par_31_60               NUMERIC(18,2) NOT NULL DEFAULT 0,
    par_61_90               NUMERIC(18,2) NOT NULL DEFAULT 0,
    par_90_plus             NUMERIC(18,2) NOT NULL DEFAULT 0,

    collateral_value_total  NUMERIC(18,2) NOT NULL DEFAULT 0,
    collateral_value_at_risk NUMERIC(18,2) NOT NULL DEFAULT 0,

    expense_today           NUMERIC(18,2) NOT NULL DEFAULT 0,
    interest_income_mtd     NUMERIC(18,2) NOT NULL DEFAULT 0,
    expense_mtd             NUMERIC(18,2) NOT NULL DEFAULT 0,

    by_loan_type            JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (financier_id, snapshot_date)
);


-- =====================================================================
--  SECTION 4 — ROW LEVEL SECURITY
--  The app connects as a non-superuser and sets, per request:
--     SET LOCAL app.current_financier_id = '<uuid>';
--  Login resolves mobile -> financier via a separate auth role that
--  reads only the financiers table.
-- =====================================================================

CREATE OR REPLACE FUNCTION current_financier_id() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('app.current_financier_id', TRUE), '')::UUID;
$$ LANGUAGE SQL STABLE;

DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
      'brokers','customers','documents','loans','loan_personal_details',
      'loan_vehicle_details','loan_gold_details','loan_gold_items',
      'loan_document_details','loan_schedule','loan_transactions',
      'seizures','running_costs','running_cost_payments','petty_expenses',
      'financier_settings','daily_snapshots'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY;', t);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I USING (financier_id = current_financier_id())
         WITH CHECK (financier_id = current_financier_id());', t);
  END LOOP;
END $$;

-- NOT under RLS, by design: financiers, sessions, otp_requests,
-- vehicle_pledge_registry, vehicle_inspection_queries, gold_rates,
-- audit_log. These are the shared/auth layer and are reached only
-- through dedicated service roles with narrow read contracts.


-- =====================================================================
--  SECTION 5 — VIEWS
-- =====================================================================

-- 5.1 Expiration Report — a view, not a table. Copying expiry dates into
--     their own table guarantees they drift out of sync.
CREATE OR REPLACE VIEW v_document_expirations AS
SELECT l.financier_id, l.id AS loan_id, l.loan_number, l.customer_id,
       c.full_name, c.mobile, c.area, c.district,
       l.loan_type, l.outstanding_principal,
       'insurance'::TEXT AS document_type,
       v.insurance_policy_number AS reference,
       v.insurance_expiry AS expiry_date,
       (v.insurance_expiry - CURRENT_DATE) AS days_remaining
FROM loans l
JOIN loan_vehicle_details v ON v.loan_id = l.id
JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active'
UNION ALL
SELECT l.financier_id, l.id, l.loan_number, l.customer_id, c.full_name, c.mobile,
       c.area, c.district, l.loan_type, l.outstanding_principal,
       'permit', v.permit_number, v.permit_expiry, (v.permit_expiry - CURRENT_DATE)
FROM loans l JOIN loan_vehicle_details v ON v.loan_id = l.id
JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active' AND v.permit_expiry IS NOT NULL
UNION ALL
SELECT l.financier_id, l.id, l.loan_number, l.customer_id, c.full_name, c.mobile,
       c.area, c.district, l.loan_type, l.outstanding_principal,
       'fitness_certificate', v.vehicle_number_raw, v.fitness_expiry, (v.fitness_expiry - CURRENT_DATE)
FROM loans l JOIN loan_vehicle_details v ON v.loan_id = l.id
JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active' AND v.fitness_expiry IS NOT NULL
UNION ALL
SELECT l.financier_id, l.id, l.loan_number, l.customer_id, c.full_name, c.mobile,
       c.area, c.district, l.loan_type, l.outstanding_principal,
       'driving_licence', v.licence_number, v.licence_expiry, (v.licence_expiry - CURRENT_DATE)
FROM loans l JOIN loan_vehicle_details v ON v.loan_id = l.id
JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active' AND v.licence_expiry IS NOT NULL
UNION ALL
SELECT l.financier_id, l.id, l.loan_number, l.customer_id, c.full_name, c.mobile,
       c.area, c.district, l.loan_type, l.outstanding_principal,
       'encumbrance_certificate', d.ec_number, d.ec_valid_until, (d.ec_valid_until - CURRENT_DATE)
FROM loans l JOIN loan_document_details d ON d.loan_id = l.id
JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active' AND d.ec_valid_until IS NOT NULL
UNION ALL
SELECT l.financier_id, l.id, l.loan_number, l.customer_id, c.full_name, c.mobile,
       c.area, c.district, l.loan_type, l.outstanding_principal,
       'loan_maturity', l.loan_number, l.maturity_date, (l.maturity_date - CURRENT_DATE)
FROM loans l JOIN customers c ON c.id = l.customer_id
WHERE l.status = 'active' AND l.maturity_date IS NOT NULL;


-- 5.2 Current Dues  (no photo, no loan ID, no agent — matches the v2 screen)
CREATE OR REPLACE VIEW v_current_dues AS
SELECT s.financier_id, s.id AS schedule_id, s.loan_id,
       c.full_name, c.mobile, c.area, c.district,
       l.loan_type,
       s.due_date,
       s.due_total,
       s.paid_total,
       (s.due_total - s.paid_total) AS balance_due,
       (s.due_date - CURRENT_DATE)  AS days_to_due,
       b.name AS broker_name
FROM loan_schedule s
JOIN loans l     ON l.id = s.loan_id
JOIN customers c ON c.id = l.customer_id
LEFT JOIN brokers b ON b.id = l.broker_id
WHERE l.status = 'active'
  AND s.status IN ('pending','due','partially_paid')
  AND s.due_date >= CURRENT_DATE;


-- 5.3 Pending Dues  (no colour coding — bucket is a plain text column)
CREATE OR REPLACE VIEW v_pending_dues AS
SELECT s.financier_id, s.id AS schedule_id, s.loan_id,
       c.full_name, c.mobile, c.area, c.district,
       l.loan_type,
       s.due_date,
       s.days_past_due,
       (s.due_principal - s.paid_principal) AS overdue_principal,
       (s.due_interest  - s.paid_interest)  AS overdue_interest,
       (s.penal_accrued - s.paid_penal)     AS overdue_penal,
       (s.due_total + s.penal_accrued - s.paid_total - s.paid_penal) AS total_overdue,
       CASE
         WHEN s.days_past_due BETWEEN 1  AND 30 THEN '1-30'
         WHEN s.days_past_due BETWEEN 31 AND 60 THEN '31-60'
         WHEN s.days_past_due BETWEEN 61 AND 90 THEN '61-90'
         WHEN s.days_past_due > 90              THEN '90+'
         ELSE 'current'
       END AS aging_bucket,
       l.last_payment_date,
       l.collateral_valuation,
       b.name AS broker_name
FROM loan_schedule s
JOIN loans l     ON l.id = s.loan_id
JOIN customers c ON c.id = l.customer_id
LEFT JOIN brokers b ON b.id = l.broker_id
WHERE l.status IN ('active','seized')
  AND s.status IN ('overdue','partially_paid','due')
  AND s.days_past_due > 0;


-- 5.4 Seizure status, derived from dates (the status enum was removed)
CREATE OR REPLACE VIEW v_seizure_status AS
SELECT sz.*,
       CASE
         WHEN sz.release_date IS NOT NULL THEN 'Released'
         WHEN sz.sale_date    IS NOT NULL THEN 'Sold'
         ELSE 'In custody'
       END AS derived_status
FROM seizures sz;


-- =====================================================================
--  SECTION 6 — TRIGGERS
-- =====================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['financiers','brokers','customers','loans','loan_schedule',
                           'seizures','running_costs','vehicle_pledge_registry',
                           'financier_settings'] LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_%I_updated BEFORE UPDATE ON %I
         FOR EACH ROW EXECUTE FUNCTION set_updated_at();', t, t);
  END LOOP;
END $$;


-- Auto-register the shared vehicle pledge.
-- v2: loans are created directly as ACTIVE, so registration fires on INSERT
-- as well as on the status change out of active.
CREATE OR REPLACE FUNCTION register_vehicle_pledge() RETURNS TRIGGER AS $$
DECLARE v RECORD;
BEGIN
  IF NEW.loan_type <> 'vehicle' OR NEW.status <> 'active' THEN RETURN NEW; END IF;

  SELECT * INTO v FROM loan_vehicle_details WHERE loan_id = NEW.id;
  IF NOT FOUND THEN RETURN NEW; END IF;   -- details saved after the loan row

  INSERT INTO vehicle_pledge_registry (
      vehicle_number_norm, vehicle_number_raw, chassis_number_norm, engine_number_norm,
      vehicle_type, make, model, manufacturing_year,
      financier_id, loan_id, status, pledged_on, source)
  VALUES (
      v.vehicle_number_norm, v.vehicle_number_raw, v.chassis_number_norm, v.engine_number_norm,
      v.vehicle_type, v.make, v.model, v.manufacturing_year,
      NEW.financier_id, NEW.id, 'active', COALESCE(NEW.disbursal_date, CURRENT_DATE), 'auto')
  ON CONFLICT DO NOTHING;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Auto-release the pledge when the loan ends, or when a seized vehicle is
-- sold or returned. Leaving it open marks the vehicle as falsely pledged
-- for every other company on the platform.
CREATE OR REPLACE FUNCTION release_vehicle_pledge() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.loan_type = 'vehicle'
     AND NEW.status IN ('closed','foreclosed','written_off')
     AND OLD.status NOT IN ('closed','foreclosed','written_off') THEN
    UPDATE vehicle_pledge_registry
       SET status = 'closed', released_on = CURRENT_DATE, updated_at = now()
     WHERE loan_id = NEW.id AND status = 'active';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_pledge_register
  AFTER INSERT ON loans
  FOR EACH ROW EXECUTE FUNCTION register_vehicle_pledge();

CREATE TRIGGER trg_loan_pledge_release
  AFTER UPDATE OF status ON loans
  FOR EACH ROW EXECUTE FUNCTION release_vehicle_pledge();


-- Seizure disposal also closes the pledge
CREATE OR REPLACE FUNCTION seizure_release_pledge() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.collateral_type = 'vehicle'
     AND (NEW.sale_date IS NOT NULL OR NEW.release_date IS NOT NULL) THEN
    UPDATE vehicle_pledge_registry
       SET status = 'closed', released_on = CURRENT_DATE, updated_at = now()
     WHERE loan_id = NEW.loan_id AND status = 'active';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_seizure_pledge_release
  AFTER INSERT OR UPDATE ON seizures
  FOR EACH ROW EXECUTE FUNCTION seizure_release_pledge();


-- =====================================================================
--  SECTION 7 — SEED DATA
-- =====================================================================

INSERT INTO expense_categories (financier_id, name, kind) VALUES
  (NULL,'Office rent','running'),          (NULL,'Employee salary','running'),
  (NULL,'Electricity','running'),          (NULL,'Water','running'),
  (NULL,'Internet / phone','running'),     (NULL,'Software subscription','running'),
  (NULL,'Vehicle EMI','running'),          (NULL,'Insurance premium','running'),
  (NULL,'Accounting fees','running'),      (NULL,'Licence renewal','running'),
  (NULL,'Borrowed capital interest','running'), (NULL,'Other recurring','running'),
  (NULL,'Transportation / fuel','petty'),  (NULL,'Food & refreshments','petty'),
  (NULL,'Stationery & printing','petty'),  (NULL,'Courier & postage','petty'),
  (NULL,'Mobile recharge','petty'),        (NULL,'Repairs & maintenance','petty'),
  (NULL,'Legal & documentation','petty'),  (NULL,'Seizure costs','petty'),
  (NULL,'Broker payment','petty'),         (NULL,'Bank charges','petty'),
  (NULL,'Miscellaneous','petty');

-- =====================================================================
--  END OF SCHEMA v2.0
-- =====================================================================
