INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('2168014f-9316-4760-9d82-d0306d5f59e4','drv_pol_payment_status', '{
        "id": "2168014f-9316-4760-9d82-d0306d5f59e4",
        "name": "drv_pol_payment_status",
        "fromClause": "drv_pol_payment_status",
        "root": false,
        "private" : true,
        "columns": [
            {
                "name": "payment_status",
                "dataType":{
                    "dataType": "stringType"
                },
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
