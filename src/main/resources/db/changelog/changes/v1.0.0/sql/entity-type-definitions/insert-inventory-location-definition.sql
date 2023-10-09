INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b','src_inventory_location', '{
        "id": "a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b",
        "name": "src_inventory_location",
        "labelAlias": "Location",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
                "labelAlias": "Id",
                "visibleByDefault": true
            },
            {
                "name": "location_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Location name",
                "visibleByDefault": true
            },
            {
                 "name": "location_code",
                 "dataType":{
                     "dataType": "stringType"
                 },
                 "valueGetter": {
                     "type": "rmb_jsonb",
                     "param": "code"
                 },
                 "labelAlias": "Location code",
                 "visibleByDefault": true
             }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
