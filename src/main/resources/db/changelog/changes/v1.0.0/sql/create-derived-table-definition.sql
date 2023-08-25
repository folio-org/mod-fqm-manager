CREATE TABLE entity_type_definition
(
    id                     UUID NOT NULL PRIMARY KEY,
    derived_table_name     VARCHAR(128) NOT NULL,
    definition             json  NOT NULL
);
