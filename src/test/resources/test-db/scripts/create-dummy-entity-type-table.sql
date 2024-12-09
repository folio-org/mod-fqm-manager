CREATE TABLE source_1 (
    id UUID PRIMARY KEY,
    "column_01" TEXT,
    "column_02" TEXT
);

INSERT INTO source_1 (id, "column_01", "column_02")
VALUES
    ('123e4567-e89b-12d3-a456-426614174000', 'Value1', 'Value2'),
    ('223e4567-e89b-12d3-a456-426614174001', 'Value3', 'Value4');
