INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('6b335e41-2654-4e2a-9b4e-c6930b330ccc','src_organization_types', '{
        "id": "6b335e41-2654-4e2a-9b4e-c6930b330ccc",
        "name": "src_organization_types",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
                "visibleByDefault": true
            },
            {
                "name": "organization_types_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
