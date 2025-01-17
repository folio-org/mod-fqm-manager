CREATE TABLE IF NOT EXISTS custom_fields_table (
  id UUID NOT NULL PRIMARY KEY,
  jsonb jsonb
);
INSERT INTO custom_fields_table (id, jsonb)
VALUES (
    '2c4e9797-422f-4962-a302-174af09b23f8',
    '{
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
    }'
  ),
  (
    '2c4e9797-422f-4962-a302-174af09b23f9',
    '{
       "id": "2c4e9797-422f-4962-a302-174af09b23f9",
       "name": "custom_column_2",
       "type": "RADIO_BUTTON",
       "order": 2,
       "refId": "customColumn2",
       "visible": true,
       "entityType": "user",
       "selectField": {
          "options": {
            "values": [
               {
                 "id": "opt1",
                 "value": "label1",
                 "default": true
               },
               {
                 "id": "opt2",
                 "value": "label2",
                 "default": false
               }
            ]
          }
        }
     }'
  ),
  (
    '2c4e9797-422f-4962-a302-174af09b23fa',
    '{
       "id": "2c4e9797-422f-4962-a302-174af09b23fa",
       "name": "custom_column_3",
       "type": "SINGLE_SELECT_DROPDOWN",
       "order": 3,
       "refId": "customColumn3",
       "visible": true,
       "entityType": "user",
       "selectField": {
         "options": {
           "values": [
              {
                "id": "opt3",
                "value": "label3",
                "default": true
              },
              {
                "id": "opt4",
                "value": "label4",
                "default": false
              }
           ]
         }
       }
     }'
    ),
  (
    '2c4e9797-422f-4962-a302-174af09b23fb',
    '{
       "id": "2c4e9797-422f-4962-a302-174af09b23fb",
       "name": "invalid_custom_column",
       "type": "SINGLE_SELECT_DROPDOWN",
       "order": 4,
       "refId": "invalidCustomColumn",
       "visible": true,
       "entityType": "user",
       "selectField": {}
    }'
  );
CREATE VIEW custom_fields_source_view AS
SELECT *
FROM custom_fields_table;
