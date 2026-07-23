CREATE TABLE telegram_users (
    id                    BIGSERIAL PRIMARY KEY,
    telegram_user_id      BIGINT NOT NULL UNIQUE,
    chat_id               BIGINT NOT NULL,
    username              VARCHAR(255),
    first_name            VARCHAR(255),
    last_name             VARCHAR(255),
    conversation_state    VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    conversation_context  TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stations (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(20) NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    name_normalized  VARCHAR(255) NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_stations_name_normalized ON stations (name_normalized);

CREATE TABLE ticket_subscriptions (
    id                     BIGSERIAL PRIMARY KEY,
    telegram_user_id       BIGINT NOT NULL REFERENCES telegram_users (telegram_user_id),
    from_station_code      VARCHAR(20) NOT NULL,
    from_station_name      VARCHAR(255) NOT NULL,
    to_station_code        VARCHAR(20) NOT NULL,
    to_station_name        VARCHAR(255) NOT NULL,
    start_date             DATE NOT NULL,
    end_date               DATE NOT NULL,
    current_best_date      DATE,
    current_result_json    TEXT,
    current_fingerprint    VARCHAR(128),
    filters_json           TEXT NOT NULL DEFAULT '{}',
    status                 VARCHAR(20) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_checked_at        TIMESTAMPTZ,
    next_check_at          TIMESTAMPTZ,
    error_count            INT NOT NULL DEFAULT 0,
    last_error             TEXT,
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_subscriptions_status_next_check ON ticket_subscriptions (status, next_check_at);
CREATE INDEX idx_ticket_subscriptions_telegram_user_id ON ticket_subscriptions (telegram_user_id);

CREATE TABLE subscription_notifications (
    id                    BIGSERIAL PRIMARY KEY,
    subscription_id       BIGINT NOT NULL REFERENCES ticket_subscriptions (id),
    result_date           DATE NOT NULL,
    fingerprint           VARCHAR(128) NOT NULL,
    telegram_message_id   BIGINT,
    sent_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                VARCHAR(20) NOT NULL,
    error_message         TEXT
);

CREATE INDEX idx_subscription_notifications_subscription_id ON subscription_notifications (subscription_id);
CREATE UNIQUE INDEX uq_subscription_notifications_dedup ON subscription_notifications (subscription_id, fingerprint);

CREATE TABLE search_logs (
    id                   BIGSERIAL PRIMARY KEY,
    telegram_user_id     BIGINT,
    from_station_code    VARCHAR(20) NOT NULL,
    to_station_code      VARCHAR(20) NOT NULL,
    start_date           DATE NOT NULL,
    end_date             DATE NOT NULL,
    found_date           DATE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_logs_telegram_user_id ON search_logs (telegram_user_id);
