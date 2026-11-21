CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(40) NOT NULL,
    birth_date DATE NOT NULL,
    region VARCHAR(100) NOT NULL DEFAULT '',
    budget INTEGER NOT NULL CHECK (budget BETWEEN 0 AND 10000000),
    equipment TEXT NOT NULL DEFAULT '[]',
    notifications BOOLEAN NOT NULL DEFAULT TRUE,
    failed_logins INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS agent_task (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    data JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS agent_task_user_idx ON agent_task(user_id, updated_at DESC);

-- Catalogue discovery is separate from user tasks and authoritative external bookings.
CREATE TABLE IF NOT EXISTS resource_source (
    code VARCHAR(40) PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    homepage TEXT NOT NULL CHECK (homepage ~ '^https://[^[:space:]]+$'),
    availability_method VARCHAR(10) NOT NULL DEFAULT 'NONE' CHECK (availability_method IN ('NONE','API','BROWSER')),
    booking_method VARCHAR(10) NOT NULL DEFAULT 'NONE' CHECK (booking_method IN ('NONE','API','BROWSER')),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('SPACE','ITEM')),
    sector VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN' CHECK (sector IN ('PUBLIC','PRIVATE','UNKNOWN')),
    category VARCHAR(60) NOT NULL CHECK (btrim(category) <> ''),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    operator_name VARCHAR(200),
    region_code VARCHAR(10) CHECK (region_code ~ '^[0-9]{10}$'),
    sido VARCHAR(40) NOT NULL CHECK (btrim(sido) <> ''),
    sigungu VARCHAR(80) NOT NULL DEFAULT '',
    eupmyeondong VARCHAR(80) NOT NULL DEFAULT '',
    address TEXT NOT NULL CHECK (btrim(address) <> ''),
    latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10,6) CHECK (longitude BETWEEN -180 AND 180),
    equipment TEXT[] NOT NULL DEFAULT '{}',
    capacity_people INTEGER CHECK (capacity_people > 0),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CHECK (kind = 'SPACE' OR capacity_people IS NULL)
);
CREATE INDEX IF NOT EXISTS resource_region_idx ON resource(sido, sigungu, eupmyeondong, kind, sector) WHERE active;
CREATE INDEX IF NOT EXISTS resource_category_idx ON resource(kind, category) WHERE active;
CREATE INDEX IF NOT EXISTS resource_region_code_idx ON resource(region_code) WHERE active;
CREATE INDEX IF NOT EXISTS resource_equipment_idx ON resource USING GIN(equipment) WHERE active;

-- A room/item can have multiple provider listings. Match physical resources explicitly.
CREATE TABLE IF NOT EXISTS resource_listing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resource(id),
    source_code VARCHAR(40) NOT NULL REFERENCES resource_source(code),
    external_id VARCHAR(200) NOT NULL CHECK (btrim(external_id) <> ''),
    source_url TEXT NOT NULL CHECK (source_url ~ '^https://[^[:space:]]+$'),
    booking_url TEXT CHECK (booking_url ~ '^https://[^[:space:]]+$'),
    booking_channel VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN' CHECK (booking_channel IN ('ONLINE','PHONE','VISIT','NONE','UNKNOWN')),
    price_amount BIGINT CHECK (price_amount >= 0),
    price_unit VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN' CHECK (price_unit IN ('HOUR','DAY','BOOKING','ITEM','UNKNOWN')),
    price_quote TEXT,
    quantity_total INTEGER CHECK (quantity_total >= 0),
    eligibility TEXT,
    cancellation TEXT,
    evidence TEXT,
    fetched_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(source_code, external_id),
    CHECK (price_amount IS NOT NULL OR price_unit = 'UNKNOWN')
);
CREATE INDEX IF NOT EXISTS resource_listing_resource_idx ON resource_listing(resource_id) WHERE active;
CREATE INDEX IF NOT EXISTS resource_listing_sync_idx ON resource_listing(source_code, fetched_at);

-- Exact requested interval + quantity only; this cache never holds or reserves inventory.
CREATE TABLE IF NOT EXISTS resource_availability (
    listing_id UUID NOT NULL REFERENCES resource_listing(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    status VARCHAR(12) NOT NULL CHECK (status IN ('AVAILABLE','UNAVAILABLE','UNKNOWN','ERROR')),
    remaining_quantity INTEGER CHECK (remaining_quantity >= 0),
    total_price BIGINT CHECK (total_price >= 0),
    evidence TEXT,
    failure_reason TEXT,
    checked_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (listing_id, starts_at, ends_at, requested_quantity),
    CHECK (ends_at > starts_at),
    CHECK (expires_at > checked_at),
    CHECK (status <> 'AVAILABLE' OR evidence IS NOT NULL AND btrim(evidence) <> ''),
    CHECK (status <> 'AVAILABLE' OR remaining_quantity IS NULL OR remaining_quantity >= requested_quantity)
);
CREATE INDEX IF NOT EXISTS resource_availability_expiry_idx ON resource_availability(expires_at);

CREATE OR REPLACE VIEW current_resource_availability AS
SELECT a.listing_id, a.starts_at, a.ends_at, a.requested_quantity,
       CASE WHEN a.checked_at <= CURRENT_TIMESTAMP AND a.expires_at > CURRENT_TIMESTAMP
                 AND l.active AND r.active AND s.enabled AND s.availability_method <> 'NONE'
                 AND l.booking_channel = 'ONLINE'
            THEN a.status ELSE 'UNKNOWN' END AS status,
       a.remaining_quantity, a.total_price, a.evidence, a.failure_reason, a.checked_at, a.expires_at
FROM resource_availability a
JOIN resource_listing l ON l.id = a.listing_id
JOIN resource r ON r.id = l.resource_id
JOIN resource_source s ON s.code = l.source_code;

-- Registry only: credentials, integrations and real inventory are not seeded here.
INSERT INTO resource_source(code, name, homepage) VALUES
    ('eshare', '공유누리', 'https://www.eshare.go.kr/'),
    ('seoul_reservation', '서울시 공공서비스예약', 'https://yeyak.seoul.go.kr/'),
    ('naver_booking', '네이버 예약', 'https://booking.naver.com/')
ON CONFLICT (code) DO NOTHING;
