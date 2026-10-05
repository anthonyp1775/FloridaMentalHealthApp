-- =====================================================================
-- CarePath FL - Mental Health Navigation (State of Florida)
-- MySQL 8 schema
--
-- Run order:  schema.sql  then  seed.sql
-- Conventions: snake_case, InnoDB, utf8mb4,
--              enums as VARCHAR + CHECK, timestamps as DATETIME
--
-- SCOPE NOTE: this system stores CONTACT and PREFERENCE data only.
-- No diagnoses, clinical notes, assessments or treatment history are
-- stored anywhere in this schema - that is a deliberate boundary, not
-- an omission. All data is synthetic.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS fl_mental_health_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fl_mental_health_db;

-- Drop in reverse dependency order so a re-run is always clean.
--
-- FOREIGN_KEY_CHECKS is disabled for this block as a safety net: the
-- order below is correct, but one mis-ordered line leaves the database
-- half-dropped and the next CREATE fails confusingly. Belt and braces.
--
-- Order note: client_profiles must come BEFORE counties, languages and
-- insurance_plans - it holds foreign keys into all three.
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS referral_status_history;
DROP TABLE IF EXISTS referral_requests;
DROP TABLE IF EXISTS saved_providers;
DROP TABLE IF EXISTS client_profiles;
DROP TABLE IF EXISTS provider_insurance;
DROP TABLE IF EXISTS provider_languages;
DROP TABLE IF EXISTS provider_populations;
DROP TABLE IF EXISTS provider_specialties;
DROP TABLE IF EXISTS providers;
DROP TABLE IF EXISTS organizations;
DROP TABLE IF EXISTS insurance_plans;
DROP TABLE IF EXISTS languages;
DROP TABLE IF EXISTS populations;
DROP TABLE IF EXISTS specialties;
DROP TABLE IF EXISTS counties;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;

SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
-- SECURITY
-- =====================================================================

-- Names stored WITH the ROLE_ prefix so hasRole("ADMIN") matches
-- without adding the prefix in code.
--   ROLE_USER  - a person seeking care
--   ROLE_ADMIN - navigator / clinic staff reviewing referrals
CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB;


CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(120) NOT NULL,
    -- BCrypt hash, always 60 chars. Never plaintext.
    password    VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Email is the login field; this UNIQUE also gives the lookup index.
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;


CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    INDEX idx_user_roles_role (role_id)
) ENGINE=InnoDB;


-- =====================================================================
-- REFERENCE DATA (Florida-specific)
-- =====================================================================

-- Florida's 67 counties. managing_entity is the regional behavioral
-- health contractor that administers state-funded services for that
-- county - a real feature of how Florida organizes this system.
CREATE TABLE counties (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    name            VARCHAR(60) NOT NULL,
    region          VARCHAR(40) NOT NULL,   -- Northwest, Northeast, Central, Southeast, Southwest
    managing_entity VARCHAR(80),
    PRIMARY KEY (id),
    CONSTRAINT uq_counties_name UNIQUE (name),
    INDEX idx_counties_region (region)
) ENGINE=InnoDB;


-- Clinical focus areas. This is the "what do they treat" filter -
-- deliberately NOT a diagnostic instrument, just a browsable category
-- a person can recognize and select for themselves.
CREATE TABLE specialties (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uq_specialties_name UNIQUE (name)
) ENGINE=InnoDB;


-- Age group / unit of treatment: Children, Adolescents, Adults,
-- Older adults, Couples, Families, Groups.
-- A separate question from "what do they treat".
CREATE TABLE populations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(60) NOT NULL,
    age_range   VARCHAR(30),           -- display only, e.g. "13-17"
    PRIMARY KEY (id),
    CONSTRAINT uq_populations_name UNIQUE (name)
) ENGINE=InnoDB;


-- Languages a provider offers services in. In Florida, Spanish and
-- Haitian Creole are first-order access barriers, not nice-to-haves.
CREATE TABLE languages (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL,
    iso_code    VARCHAR(8),            -- 'es', 'ht', 'pt'
    PRIMARY KEY (id),
    CONSTRAINT uq_languages_name UNIQUE (name)
) ENGINE=InnoDB;


-- Medicaid managed-care plans, commercial carriers, Medicare,
-- plus the two non-insurance options people actually need to filter on.
CREATE TABLE insurance_plans (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    plan_type   VARCHAR(30)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_insurance_plans_name UNIQUE (name),
    CONSTRAINT chk_insurance_plan_type
        CHECK (plan_type IN ('MEDICAID', 'MEDICARE', 'COMMERCIAL',
                             'MARKETPLACE', 'SLIDING_SCALE', 'SELF_PAY')),
    INDEX idx_insurance_plans_type (plan_type)
) ENGINE=InnoDB;


-- =====================================================================
-- ORGANIZATIONS
-- The clinic or agency. Carries the address, and therefore the county -
-- which is why location search joins provider -> organization -> county
-- rather than duplicating a county FK onto every provider row.
-- =====================================================================

CREATE TABLE organizations (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(160) NOT NULL,
    org_type          VARCHAR(40)  NOT NULL DEFAULT 'PRIVATE_PRACTICE',

    county_id         BIGINT       NOT NULL,
    address_line1     VARCHAR(160),
    address_line2     VARCHAR(160),
    city              VARCHAR(80),
    postal_code       VARCHAR(10),

    phone             VARCHAR(30),
    website           VARCHAR(255),

    -- Florida's involuntary examination law is the Baker Act, and
    -- designated receiving facilities are a real category in the state
    -- system. This flag is INFORMATIONAL ONLY - nothing in this app
    -- initiates, recommends or processes an involuntary examination.
    baker_act_receiving_facility BOOLEAN NOT NULL DEFAULT FALSE,

    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_organizations_name UNIQUE (name),
    CONSTRAINT fk_organizations_county
        FOREIGN KEY (county_id) REFERENCES counties (id) ON DELETE RESTRICT,
    CONSTRAINT chk_organizations_type
        CHECK (org_type IN ('PRIVATE_PRACTICE', 'GROUP_PRACTICE',
                            'COMMUNITY_MENTAL_HEALTH_CENTER', 'HOSPITAL',
                            'CRISIS_STABILIZATION_UNIT', 'RESIDENTIAL',
                            'FQHC', 'TELEHEALTH_ONLY', 'NONPROFIT')),
    INDEX idx_organizations_county (county_id),
    INDEX idx_organizations_type   (org_type),
    INDEX idx_organizations_active (is_active)
) ENGINE=InnoDB;


-- =====================================================================
-- PROVIDERS
-- The searchable core of the application.
-- =====================================================================

CREATE TABLE providers (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,

    first_name           VARCHAR(60)  NOT NULL,
    last_name            VARCHAR(60)  NOT NULL,

    -- One credential per provider, so a column rather than a join table.
    -- Matters enormously to users because it determines who can
    -- prescribe medication. These are Florida's actual license types
    -- (Ch. 490 psychologists, Ch. 491 LMHC/LCSW/LMFT).
    credential           VARCHAR(30)  NOT NULL,

    -- FL license number. Nullable because peer specialists and
    -- registered interns are credentialed differently.
    license_number       VARCHAR(30),

    organization_id      BIGINT       NOT NULL,

    bio                  TEXT,
    years_experience     INT          NOT NULL DEFAULT 0,

    -- Access modes. A provider may offer either, both, or neither
    -- (neither = not currently seeing clients).
    offers_telehealth    BOOLEAN      NOT NULL DEFAULT FALSE,
    offers_in_person     BOOLEAN      NOT NULL DEFAULT TRUE,

    -- CAPACITY. This is the analog of stock in an inventory system:
    -- open_slots is decremented inside the @Transactional accept flow,
    -- and every change is written to referral_status_history.
    accepting_new_clients BOOLEAN     NOT NULL DEFAULT TRUE,
    open_slots           INT          NOT NULL DEFAULT 0,
    waitlist_count       INT          NOT NULL DEFAULT 0,
    typical_wait_days    INT,                       -- NULL = unknown

    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_providers_license UNIQUE (license_number),

    CONSTRAINT fk_providers_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_providers_credential
        CHECK (credential IN ('PSYCHIATRIST', 'PSYCHIATRIC_ARNP',
                              'PSYCHOLOGIST', 'LMHC', 'LCSW', 'LMFT',
                              'REGISTERED_INTERN', 'CAP',
                              'PEER_SPECIALIST')),
    CONSTRAINT chk_providers_open_slots  CHECK (open_slots >= 0),
    CONSTRAINT chk_providers_waitlist    CHECK (waitlist_count >= 0),
    CONSTRAINT chk_providers_experience  CHECK (years_experience >= 0),

    INDEX idx_providers_organization (organization_id),
    INDEX idx_providers_credential   (credential),
    INDEX idx_providers_accepting    (accepting_new_clients),
    INDEX idx_providers_active       (is_active),
    INDEX idx_providers_last_name    (last_name)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- The four many-to-many relationships. Same shape each time: composite
-- primary key (so a pairing cannot be duplicated), CASCADE from the
-- provider side, RESTRICT from the lookup side, and a reverse index so
-- the filter query can start from either end.
-- ---------------------------------------------------------------------

CREATE TABLE provider_specialties (
    provider_id  BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    PRIMARY KEY (provider_id, specialty_id),
    CONSTRAINT fk_prov_spec_provider
        FOREIGN KEY (provider_id)  REFERENCES providers (id)   ON DELETE CASCADE,
    CONSTRAINT fk_prov_spec_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialties (id) ON DELETE RESTRICT,
    INDEX idx_prov_spec_specialty (specialty_id)
) ENGINE=InnoDB;


CREATE TABLE provider_populations (
    provider_id   BIGINT NOT NULL,
    population_id BIGINT NOT NULL,
    PRIMARY KEY (provider_id, population_id),
    CONSTRAINT fk_prov_pop_provider
        FOREIGN KEY (provider_id)   REFERENCES providers (id)   ON DELETE CASCADE,
    CONSTRAINT fk_prov_pop_population
        FOREIGN KEY (population_id) REFERENCES populations (id) ON DELETE RESTRICT,
    INDEX idx_prov_pop_population (population_id)
) ENGINE=InnoDB;


CREATE TABLE provider_languages (
    provider_id BIGINT NOT NULL,
    language_id BIGINT NOT NULL,
    PRIMARY KEY (provider_id, language_id),
    CONSTRAINT fk_prov_lang_provider
        FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE CASCADE,
    CONSTRAINT fk_prov_lang_language
        FOREIGN KEY (language_id) REFERENCES languages (id) ON DELETE RESTRICT,
    INDEX idx_prov_lang_language (language_id)
) ENGINE=InnoDB;


CREATE TABLE provider_insurance (
    provider_id        BIGINT NOT NULL,
    insurance_plan_id  BIGINT NOT NULL,
    PRIMARY KEY (provider_id, insurance_plan_id),
    CONSTRAINT fk_prov_ins_provider
        FOREIGN KEY (provider_id)       REFERENCES providers (id)       ON DELETE CASCADE,
    CONSTRAINT fk_prov_ins_plan
        FOREIGN KEY (insurance_plan_id) REFERENCES insurance_plans (id) ON DELETE RESTRICT,
    INDEX idx_prov_ins_plan (insurance_plan_id)
) ENGINE=InnoDB;


-- =====================================================================
-- CLIENT PROFILES
-- One-to-one with users. Preferences the person states about
-- themselves, used to pre-fill search filters. NOT clinical data.
-- =====================================================================

CREATE TABLE client_profiles (
    -- Shared primary key: the PK is also the FK to users. This is the
    -- cleanest way to model a true one-to-one - it makes a second
    -- profile row for the same user structurally impossible.
    user_id                 BIGINT      NOT NULL,

    phone                   VARCHAR(30),
    preferred_county_id     BIGINT,
    preferred_language_id   BIGINT,
    insurance_plan_id       BIGINT,

    prefers_telehealth      BOOLEAN     NOT NULL DEFAULT FALSE,
    -- How the person wants to be contacted about a referral.
    contact_preference      VARCHAR(20) NOT NULL DEFAULT 'EMAIL',

    updated_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    CONSTRAINT fk_client_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_client_profiles_county
        FOREIGN KEY (preferred_county_id) REFERENCES counties (id) ON DELETE SET NULL,
    CONSTRAINT fk_client_profiles_language
        FOREIGN KEY (preferred_language_id) REFERENCES languages (id) ON DELETE SET NULL,
    CONSTRAINT fk_client_profiles_insurance
        FOREIGN KEY (insurance_plan_id) REFERENCES insurance_plans (id) ON DELETE SET NULL,
    CONSTRAINT chk_client_contact_pref
        CHECK (contact_preference IN ('EMAIL', 'PHONE', 'TEXT')),
    INDEX idx_client_profiles_county (preferred_county_id)
) ENGINE=InnoDB;


-- =====================================================================
-- SAVED PROVIDERS (a user's shortlist)
--
-- Surrogate id + UNIQUE rather than a composite primary key: JPA needs
-- @IdClass or @EmbeddedId for a composite PK on an entity class, and
-- this table IS an entity (it has its own created_at and is read on its
-- own). The pure join tables above have no entity class, so a composite
-- PK costs nothing there.
-- =====================================================================

CREATE TABLE saved_providers (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT   NOT NULL,
    provider_id  BIGINT   NOT NULL,
    note         VARCHAR(255),
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_saved_providers UNIQUE (user_id, provider_id),
    CONSTRAINT fk_saved_providers_user
        FOREIGN KEY (user_id)     REFERENCES users (id)     ON DELETE CASCADE,
    CONSTRAINT fk_saved_providers_provider
        FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE CASCADE,
    INDEX idx_saved_providers_user (user_id)
) ENGINE=InnoDB;


-- =====================================================================
-- REFERRAL REQUESTS
-- A person asking to be connected to a provider. The core workflow.
-- =====================================================================

CREATE TABLE referral_requests (
    id                BIGINT       NOT NULL AUTO_INCREMENT,

    user_id           BIGINT       NOT NULL,   -- who is seeking care
    provider_id       BIGINT       NOT NULL,   -- who they asked for

    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    -- What the person chose to share when submitting. Free text they
    -- write themselves - the app never asks clinical screening
    -- questions and stores no diagnosis.
    message           TEXT,
    preferred_contact VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',

    submitted_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Set when the request leaves PENDING. NULL while still open.
    resolved_at       DATETIME     NULL,
    -- Staff member who resolved it. NULL while PENDING.
    resolved_by       BIGINT       NULL,

    PRIMARY KEY (id),

    -- One OPEN request per user/provider pair is enforced in the
    -- service layer, not here: MySQL cannot express "unique only when
    -- status = 'PENDING'". Documented as a deliberate choice.
    CONSTRAINT fk_referral_requests_user
        FOREIGN KEY (user_id)     REFERENCES users (id)     ON DELETE CASCADE,
    CONSTRAINT fk_referral_requests_provider
        FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_referral_requests_resolver
        FOREIGN KEY (resolved_by) REFERENCES users (id)     ON DELETE SET NULL,

    CONSTRAINT chk_referral_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'WAITLISTED',
                          'DECLINED', 'WITHDRAWN', 'CLOSED')),
    CONSTRAINT chk_referral_contact
        CHECK (preferred_contact IN ('EMAIL', 'PHONE', 'TEXT')),

    INDEX idx_referral_requests_user      (user_id),
    INDEX idx_referral_requests_provider  (provider_id),
    -- The admin queue query: status + oldest first.
    INDEX idx_referral_requests_queue     (status, submitted_at),
    INDEX idx_referral_requests_submitted (submitted_at)
) ENGINE=InnoDB;


-- =====================================================================
-- REFERRAL STATUS HISTORY
-- The audit trail. Every status change writes a row, so no referral
-- ever moves without a record of who moved it and why. Same role the
-- stock-movement ledger played in the inventory design.
-- =====================================================================

CREATE TABLE referral_status_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    referral_request_id BIGINT       NOT NULL,

    -- NULL on the first row (creation): there was no prior status.
    from_status         VARCHAR(20)  NULL,
    to_status           VARCHAR(20)  NOT NULL,

    note                VARCHAR(255),
    -- Who made the change. NULL only if the actor was removed later.
    changed_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_status_history_referral
        FOREIGN KEY (referral_request_id) REFERENCES referral_requests (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_status_history_user
        FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT chk_status_history_from
        CHECK (from_status IS NULL OR from_status IN
              ('PENDING', 'ACCEPTED', 'WAITLISTED',
               'DECLINED', 'WITHDRAWN', 'CLOSED')),
    CONSTRAINT chk_status_history_to
        CHECK (to_status IN ('PENDING', 'ACCEPTED', 'WAITLISTED',
                             'DECLINED', 'WITHDRAWN', 'CLOSED')),
    -- A change must actually change something.
    CONSTRAINT chk_status_history_moved
        CHECK (from_status IS NULL OR from_status <> to_status),

    INDEX idx_status_history_referral (referral_request_id, created_at),
    INDEX idx_status_history_user     (changed_by)
) ENGINE=InnoDB;


-- =====================================================================
-- VERIFICATION (run after seed.sql)
-- =====================================================================

-- 1. Every referral should have at least one history row, and its
--    current status should match the latest to_status. ZERO rows.
-- SELECT r.id, r.status, h.to_status AS latest_history_status
-- FROM referral_requests r
-- LEFT JOIN referral_status_history h
--        ON h.id = (SELECT MAX(h2.id)
--                   FROM referral_status_history h2
--                   WHERE h2.referral_request_id = r.id)
-- WHERE h.id IS NULL OR h.to_status <> r.status;

-- 2. A provider marked as accepting new clients but with no open slots
--    is contradictory. ZERO rows.
-- SELECT id, first_name, last_name, open_slots
-- FROM providers
-- WHERE accepting_new_clients = TRUE AND open_slots = 0 AND is_active = TRUE;

-- 3. The main search query - Spanish-speaking, Medicaid, PTSD,
--    telehealth, accepting clients. Should return a handful of rows.
-- SELECT p.id, p.first_name, p.last_name, p.credential,
--        o.name AS organization, c.name AS county, p.open_slots
-- FROM providers p
-- JOIN organizations o        ON o.id = p.organization_id
-- JOIN counties c             ON c.id = o.county_id
-- JOIN provider_specialties ps ON ps.provider_id = p.id
-- JOIN specialties s          ON s.id = ps.specialty_id
-- JOIN provider_languages pl  ON pl.provider_id = p.id
-- JOIN languages l            ON l.id = pl.language_id
-- JOIN provider_insurance pi  ON pi.provider_id = p.id
-- JOIN insurance_plans ip     ON ip.id = pi.insurance_plan_id
-- WHERE s.name = 'PTSD & Trauma'
--   AND l.name = 'Spanish'
--   AND ip.plan_type = 'MEDICAID'
--   AND p.offers_telehealth = TRUE
--   AND p.accepting_new_clients = TRUE
--   AND p.is_active = TRUE
-- GROUP BY p.id, p.first_name, p.last_name, p.credential,
--          o.name, c.name, p.open_slots;
