INSERT INTO entity_type_definition (id, derived_table_name, definition)
     VALUES ('a435d29f-ff11-4b7a-8a5f-3c5505452208','drv_languages', '{
         "id": "a435d29f-ff11-4b7a-8a5f-3c5505452208",
         "name": "drv_languages",
         "fromClause": "drv_languages",
         "root": true,
         "private" : true,
         "columns": [
             {
               "name": "languages",
               "dataType":{
                   "dataType": "stringType"
               },
               "valueGetter": "drv_languages.languages",
               "visibleByDefault": true
             }
         ]
 }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
