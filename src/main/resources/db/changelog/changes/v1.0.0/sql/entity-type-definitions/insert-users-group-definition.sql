INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('e611264d-377e-4d87-a93f-f1ca327d3db0','src_users_groups', '{
        "id": "e611264d-377e-4d87-a93f-f1ca327d3db0",
        "name": "src_users_groups",
        "labelAlias": "Groups",
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
                "name": "group",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "group"
                },
                "labelAlias": "Group",
                "visibleByDefault": true
            }
        ]
}');
