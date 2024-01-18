INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('ffb91f00-eb1c-4936-a637-f8708c967c73','user_custom_fields', '{
        "id": "ffb91f00-eb1c-4936-a637-f8708c967c73",
        "name": "user_custom_fields",
        "labelAlias": "Custom fields",
        "root": true,
        "private" : true,
        "sourceView": "user_custom_fields"
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
