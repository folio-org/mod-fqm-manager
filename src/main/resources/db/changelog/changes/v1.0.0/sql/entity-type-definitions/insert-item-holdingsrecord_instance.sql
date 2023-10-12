INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('0cb79a4c-f7eb-4941-a104-745224ae0293','drv_item_holdingsrecord_instance', '{
             "id": "0cb79a4c-f7eb-4941-a104-745224ae0293",
             "name":"drv_item_holdingsrecord_instance",
             "labelAlias" : "Items, Holdings records, Instance",
             "private" : true,
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
                     "name": "instance_created_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Instance created date",
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
                     "name": "instance_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Instance updated date",
                     "visibleByDefault": false
                },
                {
                     "name": "item_barcode",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item barcode",
                     "visibleByDefault": true
                },
                {
                     "name": "item_level_call_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item call number",
                     "visibleByDefault": false
                },
                {
                     "name": "item_level_call_number_typeid",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item call number type ID",
                     "visibleByDefault": false
                },
                {
                     "name": "item_copy_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item copy number",
                     "visibleByDefault": true
                },
                {
                     "name": "item_created_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Item created date",
                     "visibleByDefault": false
                },
                {
                     "name": "item_effective_call_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective call number",
                     "visibleByDefault": true
                },
                {
                     "name": "item_effective_call_number_typeid",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item effective call number type ID",
                     "visibleByDefault": false
                },
                 {
                     "name": "item_effective_location_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item location ID",
                     "visibleByDefault": false
                 },
                {
                      "name": "item_hrid",
                      "dataType":{
                        "dataType":"stringType"
                      },
                      "labelAlias": "Item hrid",
                      "visibleByDefault": false
                 },
                {
                     "name": "id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item ID",
                     "visibleByDefault": false
                 },
                {
                     "name": "item_material_type_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item material ID",
                     "visibleByDefault": false
                },
                {
                     "name": "item_permanent_location_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item permanent ID",
                     "visibleByDefault": false
                },
                {
                     "name": "item_status",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item status",
                     "visibleByDefault": false,
                     "source": {
                        "entityTypeId": "a1a37288-1afe-4fa5-ab59-a5bcf5d8ca2d",
                        "columnName": "item_status"
                     }
                },
                {
                     "name": "item_temporary_location_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item temporary ID",
                     "visibleByDefault": false
                },
                {
                     "name": "item_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Item updated date",
                     "visibleByDefault": false
                 }
             ]
       }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
