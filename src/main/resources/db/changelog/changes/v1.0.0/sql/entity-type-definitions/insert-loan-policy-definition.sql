INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('5e7de445-bcc6-4008-8032-8d9602b854d7','src_circulation_loan_policy', '{
        "id": "5e7de445-bcc6-4008-8032-8d9602b854d7",
        "name": "src_loan_policy_name",
        "labelAlias": "Policy name",
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
                "name": "policy_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Policy name",
                "visibleByDefault": true
            }
        ]
}') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
