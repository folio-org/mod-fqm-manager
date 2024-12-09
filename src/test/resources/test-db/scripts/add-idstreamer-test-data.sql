INSERT INTO query_details (query_id, entity_type_id, fql_query, fields, created_by, start_date, end_date, status, failure_reason)
VALUES (
    'd81b10cb-9511-4541-9646-3aaec72e41e0',
    'ddc93926-d15a-4a45-9d9c-93eadc3d9bbf',
    '{"users.username":{"$eq":"folio"},"_version":"3"}',
    '{"users.id", "users.username"}',
    '9eb67301-6f6e-468f-9b1a-6134dc39a684',
    '2024-12-09 15:09:40.654143',
    null,
    'IN_PROGRESS',
    null
),
(
    '6dbe7cf6-ef5f-40b2-a0f2-69a705cb94c8',
    'ddc93926-d15a-4a45-9d9c-93eadc3d9bbf',
    '{"users.username":{"$eq":"folio"},"_version":"3"}',
    '{"users.id","users.username"}',
    '9eb67301-6f6e-468f-9b1a-6134dc39a684',
    '2024-12-09 15:09:40.654143',
    null,
    'CANCELLED',
    null
);

INSERT INTO query_results (query_id, result_id)
VALUES
('d81b10cb-9511-4541-9646-3aaec72e41e0', '{"123e4567-e89b-12d3-a456-426614174000"}'),
('d81b10cb-9511-4541-9646-3aaec72e41e0', '{"223e4567-e89b-12d3-a456-426614174001"}');
