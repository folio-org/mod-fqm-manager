INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('0069cf6f-2833-46db-8a51-8934769b8289','drv_user_details', '{
             "id": "0069cf6f-2833-46db-8a51-8934769b8289",
             "name":"drv_user_details",
             "labelAlias" : "Users",
             "private" : false,
             "columns": [
                {
                        "name": "user_active",
                        "dataType":{
                          "dataType":"booleanType"
                        },
                        "labelAlias": "User active",
                        "visibleByDefault": true,
                        "values": [
                          {
                            "value": "true",
                            "label": "True"
                          },
                          {
                            "value": "false",
                            "label": "False"
                          }
                      ]
                  },
                {
                      "name": "user_barcode",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User barcode",
                      "visibleByDefault": true
                },
                {
                      "name": "user_created_date",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User created date",
                      "visibleByDefault": false
                },
                {
                      "name": "user_date_of_birth",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User date of birth",
                      "visibleByDefault": false
                },
                {
                      "name": "user_email",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User email",
                      "visibleByDefault": false
                },
                {
                      "name": "user_enrollment_date",
                       "dataType":{
                         "dataType":"dateType"
                       },
                       "labelAlias": "User enrollment date",
                       "visibleByDefault": false
                },
                {
                      "name": "user_expiration_date",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User expiration date",
                      "visibleByDefault": false
                },
                {
                      "name": "user_external_system_id",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User external system ID",
                      "visibleByDefault": false
                },
                 {
                     "name": "user_first_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "User first name",
                     "visibleByDefault": true
                 },
                {
                      "name": "id",
                      "dataType":{
                        "dataType":"rangedUUIDType"
                      },
                      "labelAlias": "User ID",
                      "visibleByDefault": true
                 },
                 {
                      "name": "user_last_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User last name",
                      "visibleByDefault": true
                 },
                {
                      "name": "user_middle_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User middle name",
                      "visibleByDefault": false
                },
                 {
                       "name": "user_mobile_phone",
                       "dataType":{
                         "dataType":"stringType"
                       },
                       "labelAlias": "User mobile phone",
                       "visibleByDefault": false
                 },
                 {
                       "name": "user_patron_group",
                       "dataType":{
                         "dataType":"stringType"
                       },
                       "labelAlias": "User patron group",
                       "visibleByDefault": false,
                       "idColumnName": "user_patron_group_id",
                       "source": {
                          "entityTypeId": "e611264d-377e-4d87-a93f-f1ca327d3db0",
                          "columnName": "group"
                       }
                 },
                 {
                       "name": "user_patron_group_id",
                       "dataType":{
                         "dataType":"rangedUUIDType"
                       },
                       "labelAlias": "User patron group ID",
                       "visibleByDefault": false
                 },
                {
                      "name": "user_phone",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User phone",
                      "visibleByDefault": true
                },
                 {
                       "name": "user_preferred_contact_type",
                       "dataType":{
                         "dataType":"stringType"
                       },
                       "labelAlias": "User preferred contact type",
                       "visibleByDefault": false
                },
                {
                      "name": "user_preferred_first_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User preferred first name",
                      "visibleByDefault": true
                },
                {
                      "name": "user_primary_address",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User primary address",
                      "visibleByDefault": false
                },
                {
                      "name": "user_updated_date",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User updated date",
                      "visibleByDefault": false
                },
                {
                      "name": "username",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "Username",
                      "visibleByDefault": true
                }
             ],
             "defaultSort": [
               {
                   "columnName": "id",
                   "direction": "ASC"
               }
             ]
         }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
