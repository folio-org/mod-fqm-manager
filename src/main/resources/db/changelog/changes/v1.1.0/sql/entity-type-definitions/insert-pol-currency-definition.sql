INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('b50c43a9-0e77-4d4d-934e-52ff94223969','drv_pol_currency', '{
        "id": "b50c43a9-0e77-4d4d-934e-52ff94223969",
        "name": "drv_pol_currency",
        "fromClause": "drv_pol_currency",
        "root": true,
        "private" : true,
        "columns": [
            {
              "name": "currency",
              "dataType":{
                  "dataType": "stringType"
              },
              "valueGetter": "drv_pol_currency.currency",
              "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
