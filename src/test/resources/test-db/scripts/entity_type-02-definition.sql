INSERT INTO entity_type_definition (id, definition)
VALUES (
    '0cb79a4c-f7eb-4941-a104-745224ae0292',
    '{
             "id": "0cb79a4c-f7eb-4941-a104-745224ae0292",
             "name":"entity_type-02",
             "labelAlias" : "entity_type-02",
             "customFieldEntityTypeId": "0cb79a4c-f7eb-4941-a104-745224ae0294",
             "private" : false,
             "columns": [
                 {
                     "name": "id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Entity Type ID",
                     "visibleByDefault": false
                 },
                 {
                      "name": "column-01",
                      "dataType":{
                        "dataType":"rangedUUIDType"
                      },
                      "labelAlias": "Column 1",
                      "visibleByDefault": false
                 },
                 {
                     "name": "column-02",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Column 2",
                     "visibleByDefault": true
                 }
             ],
              "defaultSort": [
                 {
                   "columnName": "column-01",
                   "direction": "ASC"
                 }
               ]
         }'
  );
