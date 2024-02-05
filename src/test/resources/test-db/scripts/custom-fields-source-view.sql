CREATE TABLE IF NOT EXISTS custom_fields_table
 (
     id      UUID NOT NULL PRIMARY KEY,
     jsonb   jsonb
 );

 INSERT INTO custom_fields_table (id, jsonb)
 VALUES ('2c4e9797-422f-4962-a302-174af09b23f8', '{
              "id": "2c4e9797-422f-4962-a302-174af09b23f8",
              "name": "custom_column_1",
              "type": "SINGLE_CHECKBOX",
              "order": 1,
              "refId": "customColumn1",
              "visible": true,
              "entityType": "user",
              "checkboxField": {
                "default": false
              }
            }'),

            ('2c4e9797-422f-4962-a302-174af09b23f9', '{
             "id": "2c4e9797-422f-4962-a302-174af09b23f9",
             "name": "custom_column_2",
             "type": "SINGLE_CHECKBOX",
             "order": 2,
             "refId": "customColumn2",
             "visible": true,
             "entityType": "user",
             "checkboxField": {
               "default": false
             }
           }');

 CREATE VIEW custom_fields_source_view AS
 SELECT * FROM custom_fields_table;
