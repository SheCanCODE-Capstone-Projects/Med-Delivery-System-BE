-- ═══════════════════════════════════════════════
--                    USERS
-- ═══════════════════════════════════════════════
CREATE TABLE users
(
    id           BIGSERIAL PRIMARY KEY,
    full_name    VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE,
    email        VARCHAR(100) UNIQUE,
    password     VARCHAR(255),
    role         VARCHAR(20)  NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITHOUT TIME ZONE,
    updated_at   TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT chk_user_role CHECK (
        role IN ('PATIENT', 'PHARMACIST', 'MANAGER', 'SUPER_ADMIN')
    ),
    CONSTRAINT chk_contact CHECK (
        phone_number IS NOT NULL OR email IS NOT NULL
    )
);

-- ═══════════════════════════════════════════════
--                USER AUTH PROVIDERS
-- ═══════════════════════════════════════════════
CREATE TABLE user_auth_providers
(
    id               BIGSERIAL PRIMARY KEY,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    linked_at        TIMESTAMP WITHOUT TIME ZONE,
    user_id          BIGINT       NOT NULL,

    CONSTRAINT fk_auth_provider_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_auth_provider_external
        UNIQUE (provider, provider_user_id),

    CONSTRAINT uk_auth_provider_user
        UNIQUE (user_id, provider)
);

CREATE INDEX idx_auth_provider_user_id
    ON user_auth_providers (user_id);

-- ═══════════════════════════════════════════════
--                PATIENT PROFILES
-- ═══════════════════════════════════════════════
CREATE TABLE patient_profiles
(
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,

    CONSTRAINT fk_patient_profile_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_patient_profile_user_id
    ON patient_profiles (user_id);

-- ═══════════════════════════════════════════════
--                PATIENT LOCATIONS
-- ═══════════════════════════════════════════════
CREATE TABLE patient_locations
(
    id                 BIGSERIAL PRIMARY KEY,
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    manual_address     VARCHAR(255),
    input_type         VARCHAR(10) NOT NULL,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    patient_profile_id BIGINT      NOT NULL UNIQUE,

    CONSTRAINT fk_location_patient_profile
        FOREIGN KEY (patient_profile_id)
            REFERENCES patient_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_location_input_type CHECK (
        input_type IN ('GPS', 'MANUAL')
    )
);

CREATE INDEX idx_patient_location_profile_id
    ON patient_locations (patient_profile_id);

-- ═══════════════════════════════════════════════
--                INSURANCE PROVIDERS
-- ═══════════════════════════════════════════════
CREATE TABLE insurance_providers
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50)  NOT NULL UNIQUE
);

CREATE INDEX idx_insurance_provider_code
    ON insurance_providers (code);

-- ═══════════════════════════════════════════════
--                INSURANCE CARDS
-- ═══════════════════════════════════════════════
CREATE TABLE insurance_cards
(
    id                 BIGSERIAL PRIMARY KEY,
    provider_name      VARCHAR(100) NOT NULL,
    member_id          VARCHAR(100) NOT NULL,
    front_image_url    VARCHAR(500) NOT NULL,
    back_image_url     VARCHAR(500) NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'UNVERIFIED',
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    patient_profile_id BIGINT       NOT NULL,

    CONSTRAINT fk_insurance_card_patient_profile
        FOREIGN KEY (patient_profile_id)
            REFERENCES patient_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_insurance_status CHECK (
        status IN (
            'UNVERIFIED',
            'PENDING_VERIFICATION',
            'VERIFIED',
            'REJECTED'
        )
    )
);

CREATE INDEX idx_insurance_card_patient_profile_id
    ON insurance_cards (patient_profile_id);

CREATE INDEX idx_insurance_card_status
    ON insurance_cards (status);

-- ═══════════════════════════════════════════════
--                PHARMACIES
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacies
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    pharmacy_code VARCHAR(50)  NOT NULL UNIQUE,
    contact_info  VARCHAR(255) NOT NULL,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    address       VARCHAR(255),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at    TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT chk_pharmacy_status CHECK (
        status IN (
            'PENDING_APPROVAL',
            'ACTIVE',
            'SUSPENDED',
            'REJECTED'
        )
    )
);

CREATE INDEX idx_pharmacy_code
    ON pharmacies (pharmacy_code);

CREATE INDEX idx_pharmacy_status
    ON pharmacies (status);

-- ═══════════════════════════════════════════════
--          PHARMACY INSURANCE PROVIDERS
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacy_insurance_providers
(
    pharmacy_id           BIGINT NOT NULL,
    insurance_provider_id BIGINT NOT NULL,

    PRIMARY KEY (pharmacy_id, insurance_provider_id),

    CONSTRAINT fk_pip_pharmacy
        FOREIGN KEY (pharmacy_id)
            REFERENCES pharmacies (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_pip_insurance_provider
        FOREIGN KEY (insurance_provider_id)
            REFERENCES insurance_providers (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_pip_pharmacy_id
    ON pharmacy_insurance_providers (pharmacy_id);

CREATE INDEX idx_pip_insurance_provider_id
    ON pharmacy_insurance_providers (insurance_provider_id);

-- ═══════════════════════════════════════════════
--                MANAGER PROFILES
-- ═══════════════════════════════════════════════
CREATE TABLE manager_profiles
(
    id           BIGSERIAL PRIMARY KEY,
    activated_at TIMESTAMP WITHOUT TIME ZONE,
    user_id      BIGINT NOT NULL UNIQUE,
    pharmacy_id  BIGINT NOT NULL UNIQUE,

    CONSTRAINT fk_manager_profile_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_manager_profile_pharmacy
        FOREIGN KEY (pharmacy_id)
            REFERENCES pharmacies (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_manager_profile_user_id
    ON manager_profiles (user_id);

CREATE INDEX idx_manager_profile_pharmacy_id
    ON manager_profiles (pharmacy_id);

-- ═══════════════════════════════════════════════
--                PHARMACIST PROFILES
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacist_profiles
(
    id                   BIGSERIAL PRIMARY KEY,
    pharmacist_unique_id VARCHAR(50) NOT NULL UNIQUE,
    user_id              BIGINT      NOT NULL UNIQUE,
    pharmacy_id          BIGINT      NOT NULL,

    CONSTRAINT fk_pharmacist_profile_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_pharmacist_profile_pharmacy
        FOREIGN KEY (pharmacy_id)
            REFERENCES pharmacies (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_pharmacist_profile_user_id
    ON pharmacist_profiles (user_id);

CREATE INDEX idx_pharmacist_profile_pharmacy_id
    ON pharmacist_profiles (pharmacy_id);

CREATE INDEX idx_pharmacist_unique_id
    ON pharmacist_profiles (pharmacist_unique_id);

-- ═══════════════════════════════════════════════
--                MEDICINES
-- ═══════════════════════════════════════════════
CREATE TABLE medicines
(
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    generic_name          VARCHAR(100),
    requires_prescription BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_medicine_name
    ON medicines (name);

-- ═══════════════════════════════════════════════
--                PHARMACY INVENTORY
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacy_inventory
(
    id                  BIGSERIAL PRIMARY KEY,
    quantity            INTEGER          NOT NULL,
    price               NUMERIC(12, 2)   NOT NULL,
    dosage_instructions VARCHAR(500),
    last_updated        TIMESTAMP WITHOUT TIME ZONE,
    pharmacy_id         BIGINT           NOT NULL,
    medicine_id         BIGINT           NOT NULL,

    CONSTRAINT fk_inventory_pharmacy
        FOREIGN KEY (pharmacy_id)
            REFERENCES pharmacies (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_inventory_medicine
        FOREIGN KEY (medicine_id)
            REFERENCES medicines (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_quantity_positive
        CHECK (quantity >= 0),

    CONSTRAINT chk_price_positive
        CHECK (price >= 0)
);

CREATE INDEX idx_inventory_pharmacy_id
    ON pharmacy_inventory (pharmacy_id);

CREATE INDEX idx_inventory_medicine_id
    ON pharmacy_inventory (medicine_id);

-- ═══════════════════════════════════════════════
--                PRESCRIPTIONS
-- ═══════════════════════════════════════════════
CREATE TABLE prescriptions
(
    id                 BIGSERIAL PRIMARY KEY,
    file_url           VARCHAR(500) NOT NULL,
    file_type          VARCHAR(10)  NOT NULL,
    notes              TEXT,
    prescription_date  DATE,
    has_stamp          BOOLEAN      NOT NULL DEFAULT FALSE,
    has_signature      BOOLEAN      NOT NULL DEFAULT FALSE,
    status             VARCHAR(30)  NOT NULL DEFAULT 'UPLOADED',
    uploaded_at        TIMESTAMP WITHOUT TIME ZONE,
    patient_profile_id BIGINT       NOT NULL,

    CONSTRAINT fk_prescription_patient_profile
        FOREIGN KEY (patient_profile_id)
            REFERENCES patient_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_file_type CHECK (
        file_type IN ('PDF', 'JPG', 'JPEG')
    ),

    CONSTRAINT chk_prescription_status CHECK (
        status IN (
            'UPLOADED',
            'SENT_TO_PHARMACY',
            'VALIDATED',
            'REJECTED'
        )
    )
);

CREATE INDEX idx_prescription_patient_profile_id
    ON prescriptions (patient_profile_id);

CREATE INDEX idx_prescription_status
    ON prescriptions (status);

-- ═══════════════════════════════════════════════
--                MEDICINE REQUESTS
-- ═══════════════════════════════════════════════
CREATE TABLE medicine_requests
(
    id                 BIGSERIAL PRIMARY KEY,
    medicine_name      VARCHAR(255),
    symptoms           TEXT,
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    patient_profile_id BIGINT NOT NULL,

    CONSTRAINT fk_medicine_request_patient_profile
        FOREIGN KEY (patient_profile_id)
            REFERENCES patient_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_medicine_or_symptoms CHECK (
        medicine_name IS NOT NULL OR symptoms IS NOT NULL
    )
);

CREATE INDEX idx_medicine_request_patient_profile_id
    ON medicine_requests (patient_profile_id);

-- ═══════════════════════════════════════════════
--                ORDERS
-- ═══════════════════════════════════════════════
CREATE TABLE orders
(
    id                     BIGSERIAL PRIMARY KEY,
    status                 VARCHAR(30)      NOT NULL DEFAULT 'UPLOADED',
    order_type             VARCHAR(30)      NOT NULL,
    fulfillment_type       VARCHAR(20),
    coverage_percentage    DOUBLE PRECISION,
    created_at             TIMESTAMP WITHOUT TIME ZONE,
    updated_at             TIMESTAMP WITHOUT TIME ZONE,
    patient_profile_id     BIGINT           NOT NULL,
    assigned_pharmacy_id   BIGINT,
    assigned_pharmacist_id BIGINT,
    prescription_id        BIGINT UNIQUE,
    medicine_request_id    BIGINT UNIQUE,

    CONSTRAINT fk_order_patient_profile
        FOREIGN KEY (patient_profile_id)
            REFERENCES patient_profiles (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_order_assigned_pharmacy
        FOREIGN KEY (assigned_pharmacy_id)
            REFERENCES pharmacies (id),

    CONSTRAINT fk_order_assigned_pharmacist
        FOREIGN KEY (assigned_pharmacist_id)
            REFERENCES pharmacist_profiles (id),

    CONSTRAINT fk_order_prescription
        FOREIGN KEY (prescription_id)
            REFERENCES prescriptions (id),

    CONSTRAINT fk_order_medicine_request
        FOREIGN KEY (medicine_request_id)
            REFERENCES medicine_requests (id),

    CONSTRAINT chk_order_status CHECK (
        status IN (
            'UPLOADED',
            'MATCHING',
            'ASSIGNED',
            'IN_PROGRESS',
            'READY_FOR_PICKUP',
            'OUT_FOR_DELIVERY',
            'COMPLETED',
            'CANCELLED'
        )
    ),

    CONSTRAINT chk_order_type CHECK (
        order_type IN (
            'PRIVATE_PURCHASE',
            'PRESCRIPTION_BASED'
        )
    ),

    CONSTRAINT chk_fulfillment_type CHECK (
        fulfillment_type IN ('PICKUP', 'DELIVERY')
        OR fulfillment_type IS NULL
    ),

    CONSTRAINT chk_order_source CHECK (
        prescription_id IS NOT NULL
        OR medicine_request_id IS NOT NULL
    )
);

CREATE INDEX idx_order_patient_profile_id
    ON orders (patient_profile_id);

CREATE INDEX idx_order_status
    ON orders (status);

CREATE INDEX idx_order_assigned_pharmacy_id
    ON orders (assigned_pharmacy_id);

-- ═══════════════════════════════════════════════
--                ORDER ITEMS
-- ═══════════════════════════════════════════════
CREATE TABLE order_items
(
    id          BIGSERIAL PRIMARY KEY,
    quantity    INTEGER          NOT NULL,
    unit_price  NUMERIC(12, 2),
    status      VARCHAR(20)      NOT NULL,
    order_id    BIGINT           NOT NULL,
    medicine_id BIGINT           NOT NULL,

    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_order_item_medicine
        FOREIGN KEY (medicine_id)
            REFERENCES medicines (id),

    CONSTRAINT chk_order_item_status CHECK (
        status IN ('AVAILABLE', 'MISSING', 'SUBSTITUTED')
    ),

    CONSTRAINT chk_order_item_quantity
        CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_order_id
    ON order_items (order_id);

CREATE INDEX idx_order_item_medicine_id
    ON order_items (medicine_id);

-- ═══════════════════════════════════════════════
--              PHARMACY MATCH RESULTS
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacy_match_results
(
    id                        BIGSERIAL PRIMARY KEY,
    coverage_percentage       DOUBLE PRECISION,
    available_medicines_count INTEGER,
    total_medicines_count     INTEGER,
    distance_in_km            DOUBLE PRECISION,
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    responded_at              TIMESTAMP WITHOUT TIME ZONE,
    order_id                  BIGINT      NOT NULL,
    pharmacy_id               BIGINT      NOT NULL,

    CONSTRAINT fk_match_result_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_match_result_pharmacy
        FOREIGN KEY (pharmacy_id)
            REFERENCES pharmacies (id),

    CONSTRAINT chk_match_result_status CHECK (
        status IN (
            'PENDING',
            'RESPONDED',
            'SELECTED',
            'REJECTED'
        )
    ),

    CONSTRAINT chk_coverage_percentage CHECK (
        coverage_percentage IS NULL OR (
            coverage_percentage >= 0 AND coverage_percentage <= 100
        )
    ),

    CONSTRAINT chk_medicines_count CHECK (
        available_medicines_count IS NULL OR available_medicines_count >= 0
    ),

    CONSTRAINT chk_total_medicines_count CHECK (
        total_medicines_count IS NULL OR total_medicines_count >= 0
    ),

    CONSTRAINT chk_medicines_count_order CHECK (
        available_medicines_count IS NULL
        OR total_medicines_count IS NULL
        OR available_medicines_count <= total_medicines_count
    ),

    CONSTRAINT chk_distance CHECK (
        distance_in_km IS NULL OR distance_in_km >= 0
    )
);

CREATE INDEX idx_match_result_order_id
    ON pharmacy_match_results (order_id);

CREATE INDEX idx_match_result_pharmacy_id
    ON pharmacy_match_results (pharmacy_id);

-- ═══════════════════════════════════════════════
--              SUBSTITUTION REQUESTS
-- ═══════════════════════════════════════════════
CREATE TABLE substitution_requests
(
    id                    BIGSERIAL PRIMARY KEY,
    reason                TEXT,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_PATIENT_APPROVAL',
    requested_at          TIMESTAMP WITHOUT TIME ZONE,
    responded_at          TIMESTAMP WITHOUT TIME ZONE,
    order_id              BIGINT      NOT NULL,
    original_medicine_id  BIGINT      NOT NULL,
    suggested_medicine_id BIGINT      NOT NULL,
    pharmacist_profile_id BIGINT      NOT NULL,

    CONSTRAINT fk_substitution_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_substitution_original_medicine
        FOREIGN KEY (original_medicine_id)
            REFERENCES medicines (id),

    CONSTRAINT fk_substitution_suggested_medicine
        FOREIGN KEY (suggested_medicine_id)
            REFERENCES medicines (id),

    CONSTRAINT fk_substitution_pharmacist
        FOREIGN KEY (pharmacist_profile_id)
            REFERENCES pharmacist_profiles (id),

    CONSTRAINT chk_substitution_status CHECK (
        status IN (
            'PENDING_PATIENT_APPROVAL',
            'APPROVED',
            'REJECTED'
        )
    )
);

CREATE INDEX idx_substitution_order_id
    ON substitution_requests (order_id);

CREATE INDEX idx_substitution_pharmacist_id
    ON substitution_requests (pharmacist_profile_id);

-- ═══════════════════════════════════════════════
--              PHARMACIST ACTION LOGS
-- ═══════════════════════════════════════════════
CREATE TABLE pharmacist_action_logs
(
    id                    BIGSERIAL PRIMARY KEY,
    action                VARCHAR(30) NOT NULL,
    description           TEXT,
    timestamp             TIMESTAMP WITHOUT TIME ZONE,
    pharmacist_profile_id BIGINT      NOT NULL,
    order_id              BIGINT      NOT NULL,

    CONSTRAINT fk_action_log_pharmacist
        FOREIGN KEY (pharmacist_profile_id)
            REFERENCES pharmacist_profiles (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_action_log_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_pharmacist_action CHECK (
        action IN (
            'PRESCRIPTION_VALIDATED',
            'STOCK_CONFIRMED',
            'SUBSTITUTION_SUGGESTED',
            'MEDICINE_DISPENSED',
            'ORDER_COMPLETED'
        )
    )
);

CREATE INDEX idx_action_log_pharmacist_id
    ON pharmacist_action_logs (pharmacist_profile_id);

CREATE INDEX idx_action_log_order_id
    ON pharmacist_action_logs (order_id);