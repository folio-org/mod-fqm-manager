INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('c8364551-7e51-475d-8473-88951181452d','src_users_departments', '{
        "id": "c8364551-7e51-475d-8473-88951181452d",
        "name": "src_users_departments",
        "labelAlias": "Departments",
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
                "name": "department",
                "dataType":{
                    "dataType": "stringType"
                },
                "valueGetter": {
                    "type": "rmb_jsonb",
                    "param": "name"
                },
                "labelAlias": "Department",
                "visibleByDefault": true
            }
        ]
}');
