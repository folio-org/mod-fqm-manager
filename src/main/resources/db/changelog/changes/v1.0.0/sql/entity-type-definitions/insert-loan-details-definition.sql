INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('4e09d89a-44ed-418e-a9cc-820dfb27bf3a','drv_loan_details', '{
             "id": "4e09d89a-44ed-418e-a9cc-820dfb27bf3a",
             "name":"drv_loan_details",
             "labelAlias" : "Loans",
             "private" : false,
             "columns": [
                 {
                     "name": "holdings_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Holdings ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "instance_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Instance ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "instance_primary_contributor",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Instance primary contributor",
                     "visibleByDefault": false
                 },
                 {
                     "name": "instance_title",
                    "dataType":{
                     "dataType":"stringType"
                     },
                     "labelAlias": "Instance title",
                     "visibleByDefault": true
                 },
                {
                     "name": "item_barcode",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "labelAlias": "Item barcode",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_call_number",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "labelAlias": "Item call number",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_id",
                     "dataType":{
                        "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_material_type",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "labelAlias": "Item material type",
                     "visibleByDefault": false,
                     "idColumnName": "item_material_type_id",
                     "source": {
                        "entityTypeId": "917ea5c8-cafe-4fa6-a942-e2388a88c6f6",
                        "columnName": "material_type_name"
                     }
                 },
                 {
                     "name": "item_material_type_id",
                     "dataType":{
                        "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item material type ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_status",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "labelAlias": "Item status",
                     "visibleByDefault": true,
                     "source": {
                        "entityTypeId": "a1a37288-1afe-4fa5-ab59-a5bcf5d8ca2d",
                        "columnName": "item_status"
                     }
                 },
                 {
                     "name": "loan_checkin_servicepoint_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Loan checkin service point ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "loan_checkin_servicepoint_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Loan checkin service point name",
                     "visibleByDefault": false,
                     "idColumnName": "loan_checkin_servicepoint_id",
                     "source": {
                        "entityTypeId": "89cdeac4-9582-4388-800b-9ccffd8d7691",
                        "columnName": "service_point_name"
                      }
                 },
                 {
                     "name": "loan_checkout_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Loan checkout date",
                     "visibleByDefault": false
                 },
                {
                     "name": "loan_checkout_servicepoint_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Loan checkout service point ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "loan_checkout_servicepoint_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Loan checkout service point name",
                     "visibleByDefault": false,
                     "idColumnName": "loan_checkout_servicepoint_id",
                     "source": {
                         "entityTypeId": "89cdeac4-9582-4388-800b-9ccffd8d7691",
                         "columnName": "service_point_name"
                       }
                 },
                 {
                     "name": "loan_due_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Loan due date",
                     "visibleByDefault": true
                 },
                 {
                     "name": "id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Loan ID",
                     "visibleByDefault": false
                 },
                {
                     "name": "loan_policy_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Loan policy ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "loan_policy_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Loan policy name",
                     "visibleByDefault": false,
                     "idColumnName": "loan_policy_id",
                     "source": {
                     "entityTypeId": "5e7de445-bcc6-4008-8032-8d9602b854d7",
                     "columnName": "policy_name"
                     }
                 },
                 {
                     "name": "loan_return_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Loan return date",
                     "visibleByDefault": false
                 },
                 {
                     "name": "loan_status",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Loan status",
                     "visibleByDefault": false,
                     "source": {
                        "entityTypeId": "146dfba5-cdc9-45f5-a8a1-3fdc454c9ae2",
                        "columnName": "loan_status"
                     }
                 },
                {
                     "name": "user_active",
                     "dataType":{
                       "dataType": "booleanType"
                     },
                     "labelAlias": "User active",
                     "visibleByDefault": false,
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
                     "name": "user_expiration_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "User expiration date",
                     "visibleByDefault": true
                 },
                 {
                     "name": "user_first_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "User first name",
                     "visibleByDefault": false
                 },
                 {
                     "name": "user_full_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "User full name",
                     "visibleByDefault": true
                 },
                 {
                     "name": "user_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "User ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "user_last_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "User last name",
                     "visibleByDefault": false
                 },
                 {
                     "name": "user_patron_group",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "User patron group",
                     "visibleByDefault": true,
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
                 }
             ],
             "defaultSort": [
                {
                    "columnName": "user_full_name",
                    "direction": "ASC"
                },
                {
                    "columnName": "loan_due_date",
                    "direction": "ASC"
                },
                {
                    "columnName": "id",
                    "direction": "ASC"
                }
              ]
         }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
