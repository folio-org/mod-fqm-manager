INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('0069cf6f-2833-46db-8a51-8934769b8289','drv_user_details', '{
             "id": "0069cf6f-2833-46db-8a51-8934769b8289",
             "name":"drv_user_details",
             "labelAlias" : "Users",
             "private" : false,
             "columns": [
                 {
                       "name": "address_ids",
                       "dataType": {
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "rangedUUIDType"
                         }
                       },
                       "labelAlias": "Address IDs",
                       "visibleByDefault": false
                 },
                 {
                       "name": "address_line1",
                       "dataType":{
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "stringType"
                         }
                       },
                       "labelAlias": "Address line1",
                       "visibleByDefault": false
                 },
                 {
                       "name": "address_line2",
                       "dataType":{
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "stringType"
                         }
                       },
                       "labelAlias": "Address line2",
                       "visibleByDefault": false
                 },
                {
                       "name": "address_type_names",
                       "dataType":{
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "stringType"
                         }
                       },
                       "labelAlias": "Address type names",
                       "visibleByDefault": false,
                       "idColumnName": "address_ids",
                        "source": {
                               "entityTypeId": "e627a89b-682b-41fe-b532-f4262020a451",
                               "columnName": "addressType"
                        }
                 },
                {
                      "name": "cities",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "labelAlias": "City",
                      "visibleByDefault": false
                },
                {
                      "name": "country_ids",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "labelAlias": "Country ID",
                      "visibleByDefault": false
                },
                {
                      "name": "department_ids",
                      "dataType": {
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "rangedUUIDType"
                        }
                      },
                      "labelAlias": "Department IDs",
                      "visibleByDefault": false
                },
                {
                      "name": "department_names",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "idColumnName": "department_ids",
                       "source": {
                              "entityTypeId": "c8364551-7e51-475d-8473-88951181452d",
                              "columnName": "department"
                       },
                      "labelAlias": "Department names",
                      "visibleByDefault": false
                },
               {
                      "name": "postal_codes",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "labelAlias": "Postal code",
                      "visibleByDefault": false
                },
                {
                      "name": "primary_address",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "Primary address",
                      "visibleByDefault": false
                },
                {
                      "name": "regions",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "labelAlias": "Region",
                      "visibleByDefault": false
                },
              {
                      "name": "active",
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
                      "name": "barcode",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User barcode",
                      "visibleByDefault": true
                },
                {
                      "name": "created_date",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User created date",
                      "visibleByDefault": false
                },
                {
                      "name": "date_of_birth",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User date of birth",
                      "visibleByDefault": false
                },
                {
                      "name": "email",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User email",
                      "visibleByDefault": false
                },
                {
                      "name": "enrollment_date",
                       "dataType":{
                         "dataType":"dateType"
                       },
                       "labelAlias": "User enrollment date",
                       "visibleByDefault": false
                },
                {
                      "name": "expiration_date",
                      "dataType":{
                        "dataType":"dateType"
                      },
                      "labelAlias": "User expiration date",
                      "visibleByDefault": false
                },
                {
                      "name": "external_system_id",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User external system ID",
                      "visibleByDefault": false
                },
                 {
                     "name": "first_name",
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
                      "name": "last_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User last name",
                      "visibleByDefault": true
                 },
                {
                      "name": "middle_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User middle name",
                      "visibleByDefault": false
                },
                 {
                       "name": "mobile_phone",
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
                      "name": "phone",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User phone",
                      "visibleByDefault": true
                },
                 {
                       "name": "preferred_contact_type",
                       "dataType":{
                         "dataType":"stringType"
                       },
                       "labelAlias": "User preferred contact type",
                       "visibleByDefault": false
                },
                {
                      "name": "preferred_first_name",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "User preferred first name",
                      "visibleByDefault": true
                },
                {
                      "name": "updated_date",
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
         }');
