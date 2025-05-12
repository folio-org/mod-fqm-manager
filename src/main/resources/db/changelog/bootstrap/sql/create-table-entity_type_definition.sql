CREATE TABLE IF NOT EXISTS entity_type_definition
(
    id                     UUID NOT NULL PRIMARY KEY,
    definition             json  NOT NULL
);
