INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('cc51f042-03e2-43d1-b1d6-11aa6a39bc78','src_acquisitions_unit', '{
        "id": "cc51f042-03e2-43d1-b1d6-11aa6a39bc78",
        "name": "src_acquisitions_unit",
        "root": true,
        "private" : true,
        "columns": [
            {
                "name": "id",
                "dataType":{
                    "dataType": "rangedUUIDType"
                },
                "visibleByDefault": true
            },
            {
                "name": "acquisitions_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
