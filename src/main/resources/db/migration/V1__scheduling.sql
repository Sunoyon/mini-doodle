CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    email varchar(254) NOT NULL UNIQUE
);

CREATE TABLE calendar (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES app_user(id)
);

CREATE TABLE slot (
    id uuid PRIMARY KEY,
    calendar_id uuid NOT NULL REFERENCES calendar(id),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(4) NOT NULL CHECK (status IN ('FREE', 'BUSY')),
    CONSTRAINT valid_interval CHECK (starts_at < ends_at),
    CONSTRAINT no_overlapping_slots EXCLUDE USING gist (
        calendar_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    )
);
CREATE INDEX slot_calendar_start ON slot(calendar_id, starts_at, id);

CREATE TABLE meeting (
    id uuid PRIMARY KEY,
    slot_id uuid NOT NULL UNIQUE REFERENCES slot(id),
    title varchar(200) NOT NULL,
    description varchar(4000) NOT NULL
);

CREATE TABLE meeting_participant (
    meeting_id uuid NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    email varchar(254) NOT NULL,
    PRIMARY KEY (meeting_id, email)
);
