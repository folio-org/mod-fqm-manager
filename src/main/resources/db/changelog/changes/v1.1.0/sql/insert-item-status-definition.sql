INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('a1a37288-1afe-4fa5-ab59-a5bcf5d8ca2d','drv_inventory_item_status_enum', '{
        "id": "a1a37288-1afe-4fa5-ab59-a5bcf5d8ca2d",
        "name": "drv_item_status",
        "labelAlias": "Item status",
        "root": false,
        "private" : true,
        "columns": [
            {
                "name": "item_status",
                "dataType":{
                    "dataType": "stringType"
                },
                "labelAlias": "Item status",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
