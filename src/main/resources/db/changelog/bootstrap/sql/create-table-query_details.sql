CREATE TABLE IF NOT EXISTS query_details
(
    query_id               UUID NOT NULL PRIMARY KEY,
    entity_type_id         UUID NOT NULL,
    fql_query              TEXT  NOT NULL,
    fields                 VARCHAR[],
    created_by             UUID NOT NULL,
    start_date             TIMESTAMP NOT NULL,
    end_date               TIMESTAMP NULL,
    status                 VARCHAR(64) CHECK (status IN ('IN_PROGRESS','SUCCESS','FAILED','CANCELLED')) NOT NULL,
    failure_reason         TEXT NULL
);
