INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('89cdeac4-9582-4388-800b-9ccffd8d7691','src_inventory_service_point', '{
        "id": "89cdeac4-9582-4388-800b-9ccffd8d7691",
        "name": "src_inventory_service_point",
        "labelAlias": "Service point",
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
                "name": "service_point_name",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Service point name",
                "visibleByDefault": true
            },
            {
                 "name": "service_point_code",
                 "dataType":{
                     "dataType": "stringType"
                 },
                 "valueGetter": {
                     "type": "rmb_jsonb",
                     "param": "code"
                 },
                 "labelAlias": "Service point code",
                 "visibleByDefault": true
             }
        ]
}');
