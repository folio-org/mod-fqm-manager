INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('9c24a719-679b-4cca-9146-42a46d721df5','src_inventory_contributor_name_type', '{
        "id": "9c24a719-679b-4cca-9146-42a46d721df5",
        "name": "src_inventory_contributor_name_type",
        "fromClause": "src_inventory_contributor_name_type",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
               "valueGetter": "src_inventory_contributor_name_type.id",
                "visibleByDefault": true
            },
            {
                "name": "contributor_name_type",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter":"src_inventory_contributor_name_type.jsonb ->> ''name''",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
