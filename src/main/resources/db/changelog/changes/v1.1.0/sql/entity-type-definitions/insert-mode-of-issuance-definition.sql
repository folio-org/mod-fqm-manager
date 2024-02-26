INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('60e315d6-db28-4077-9277-b946411fe7d9','src_inventory_mode_of_issuance', '{
        "id": "60e315d6-db28-4077-9277-b946411fe7d9",
        "name": "src_inventory_mode_of_issuance",
        "fromClause": "src_inventory_mode_of_issuance",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
               "valueGetter": "src_inventory_mode_of_issuance.id",
               "isIdColumn": true,
               "visibleByDefault": true
            },
            {
                "name": "mode_of_issuance",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": "src_inventory_mode_of_issuance.jsonb ->> ''name''",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
