INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('d2da8cc7-9171-4d3e-8aba-4da286eb5f1c','drv_inventory_statistical_code_full', '{
        "id": "d2da8cc7-9171-4d3e-8aba-4da286eb5f1c",
        "name": "drv_inventory_statistical_code_full",
        "fromClause": "drv_inventory_statistical_code_full",
        "labelAlias": "Full statistical code",
        "root": false,
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
                "name": "statistical_code",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": "drv_inventory_statistical_code_full.statistical_code",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
