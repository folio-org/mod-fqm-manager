INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('90403847-8c47-4f58-b117-9a807b052808','drv_purchase_order_line_details', '{
             "id": "90403847-8c47-4f58-b117-9a807b052808",
             "name":"drv_purchase_order_line_details",
             "private" : false,
             "columns": [
                 {
                     "name": "acqunit_ids",
                     "dataType": {
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "rangedUUIDType"
                         }
                       },
                     "visibleByDefault": false
                 },
                 {
                     "name": "acqunit_names",
                     "dataType": {
                         "dataType":"arrayType",
                         "itemDataType": {
                           "dataType": "stringType"
                         }
                       },
                     "visibleByDefault": false,
                     "idColumnName": "acqunit_ids",
                     "source": {
                        "entityTypeId": "cc51f042-03e2-43d1-b1d6-11aa6a39bc78",
                        "columnName": "acquisitions_name"
                        }
                 },
                 {
                     "name": "fund_distribution_code",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "fund_distribution_encumbrance",
                    "dataType":{
                     "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": true
                 },
                {
                     "name": "fund_distribution_type",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "fund_distribution_value",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "fund_id",
                     "dataType":{
                        "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "po_number",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_approved",
                     "dataType":{
                        "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_assigned_to",
                     "dataType":{
                        "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "purchase_order_assigned_to_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_created_by",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_created_by_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                {
                     "name": "purchase_order_created_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_created_by",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "purchase_order_line_created_by_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                {
                     "name": "purchase_order_line_created_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_description",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_estimated_price",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                {
                     "name": "purchase_order_line_exchange_rate",
                     "dataType":{
                       "dataType": "stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "purchase_order_line_payment_status",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_receipt_status",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "purchase_order_line_updated_by",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_updater_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_line_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "visibleByDefault": true
                 },
                 {
                     "name": "purchase_order_notes",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_type",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_updated_by",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_updated_by_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "purchase_order_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "visibleByDefault": false
                 },
                 {
                     "name": "vendor_code",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false,
                     "idColumnName": "vendor_id",
                     "source": {
                        "entityTypeId": "489234a9-8703-48cd-85e3-7f84011bafa3",
                        "columnName": "vendor_code"
                        }
                 },
                 {
                     "name": "vendor_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "visibleByDefault": false
                 },
                {
                     "name": "vendor_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "visibleByDefault": false,
                     "idColumnName": "vendor_id",
                     "source": {
                        "entityTypeId": "489234a9-8703-48cd-85e3-7f84011bafa3",
                        "columnName": "vendor_name"
                       }
                 }
             ],
             "defaultSort": [
               {
                   "columnName": "id",
                   "direction": "ASC"
               }
             ]
         }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
