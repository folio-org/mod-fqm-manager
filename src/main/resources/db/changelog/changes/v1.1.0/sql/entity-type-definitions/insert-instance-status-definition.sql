INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('bc03686c-657e-4f74-9d89-91eac5ea86a4','src_inventory_instance_status', '{
        "id": "bc03686c-657e-4f74-9d89-91eac5ea86a4",
        "name": "src_inventory_instance_status",
        "fromClause": "src_inventory_instance_status",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
               "valueGetter": "src_inventory_instance_status.id",
               "isIdColumn": true,
               "visibleByDefault": true
            },
            {
                "name": "status",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": "src_inventory_instance_status.jsonb ->> ''name''",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
