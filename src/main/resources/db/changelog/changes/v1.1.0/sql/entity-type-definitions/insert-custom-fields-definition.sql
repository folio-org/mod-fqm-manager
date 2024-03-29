INSERT INTO entity_type_definition (id, derived_table_name, definition)
     VALUES ('ffb91f00-eb1c-4936-a637-f8708c967c73','src_user_custom_fields', '{
         "id": "ffb91f00-eb1c-4936-a637-f8708c967c73",
         "name": "src_user_custom_fields",
         "labelAlias": "Custom fields",
         "root": true,
         "private" : true,
         "sourceView": "src_user_custom_fields",
         "sourceViewExtractor": "src_users_users.jsonb -> ''customFields''"
         }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
