INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('489234a9-8703-48cd-85e3-7f84011bafa3','src_organizations', '{
        "id": "489234a9-8703-48cd-85e3-7f84011bafa3",
        "name": "src_organizations",
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
                "name": "vendor_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "visibleByDefault": true
            },
            {
                "name": "vendor_code",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "code"
                },
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
