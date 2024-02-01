INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('5fefec2a-9d6c-474c-8698-b0ea77186c12','drv_pol_receipt_status', '{
        "id": "5fefec2a-9d6c-474c-8698-b0ea77186c12",
        "name": "drv_pol_receipt_status",
        "fromClause": "drv_pol_receipt_status",
        "root": false,
        "private" : true,
        "columns": [
            {
                "name": "receipt_status",
                "dataType":{
                    "dataType": "stringType"
                },
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
