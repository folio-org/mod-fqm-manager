INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('3553ca38-d522-439b-9f91-1512275a43b9','src_inventory_contributor_type', '{
        "id": "3553ca38-d522-439b-9f91-1512275a43b9",
        "name": "src_inventory_contributor_type",
        "fromClause": "src_inventory_contributor_type",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
               "valueGetter": "src_inventory_contributor_type.id",
                "visibleByDefault": true
            },
            {
                "name": "contributor_type",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter":"src_inventory_contributor_type.jsonb ->> ''name''",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
