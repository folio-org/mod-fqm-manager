INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('5c8315be-13f5-4df5-ae8b-086bae83484d','src_inventory_call_number_type', '{
        "id": "5c8315be-13f5-4df5-ae8b-086bae83484d",
        "name": "src_inventory_call_number_type",
        "labelAlias": "Call number type",
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
                "name": "call_number_type_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Call number type name",
                "visibleByDefault": true
            }
        ]
}');
