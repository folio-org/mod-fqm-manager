INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('917ea5c8-cafe-4fa6-a942-e2388a88c6f6','src_inventory_material_type', '{
        "id": "917ea5c8-cafe-4fa6-a942-e2388a88c6f6",
        "name": "src_inventory_material_type",
        "labelAlias": "Material type",
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
                "name": "material_type_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Material type name",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
