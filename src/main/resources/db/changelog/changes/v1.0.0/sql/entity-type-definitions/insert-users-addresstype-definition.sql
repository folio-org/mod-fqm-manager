INSERT INTO entity_type_definition (id, derived_table_name, definition)
     VALUES ('e627a89b-682b-41fe-b532-f4262020a451','src_users_addresstype', '{
         "id": "e627a89b-682b-41fe-b532-f4262020a451",
         "name": "src_users_addresstype",
         "labelAlias": "Address type",
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
                 "name": "addressType",
                 "dataType":{
                     "dataType": "stringType"
                 },
                 "valueGetter": {
                     "type": "rmb_jsonb",
                     "param": "addressType"
                 },
                 "labelAlias": "Addresstype",
                 "visibleByDefault": true
             }
         ]
 }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
