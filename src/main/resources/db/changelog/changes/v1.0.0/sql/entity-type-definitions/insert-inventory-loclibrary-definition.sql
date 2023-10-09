INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('cf9f5c11-e943-483c-913b-81d1e338accc','src_inventory_loclibrary', '{
        "id": "cf9f5c11-e943-483c-913b-81d1e338accc",
        "name": "src_inventory_loclibrary",
        "labelAlias": "Loclibrary",
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
                "name": "loclibrary_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Loclibrary name",
                "visibleByDefault": true
            },
            {
                 "name": "loclibrary_code",
                 "dataType":{
                     "dataType": "stringType"
                 },
                 "valueGetter": {
                     "type": "rmb_jsonb",
                     "param": "code"
                 },
                 "labelAlias": "Loclibrary code",
                 "visibleByDefault": true
             }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
