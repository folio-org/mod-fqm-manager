INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('0cb79a4c-f7eb-4941-a104-745224ae0292','drv_item_details', '{
             "id": "0cb79a4c-f7eb-4941-a104-745224ae0292",
             "name":"drv_item_details",
             "labelAlias" : "Items",
             "subEntityTypeIds": ["097a6f96-edd0-11ed-a05b-0242ac120003"],
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
                     "name": "item_level_call_number_type_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item call number type name",
                     "visibleByDefault": false,
                     "idColumnName": "item_level_call_number_typeid",
                     "source": {
                        "entityTypeId": "5c8315be-13f5-4df5-ae8b-086bae83484d",
                        "columnName": "call_number_type_name"
                      }
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
                     "name": "item_effective_call_number_type_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective call number type name",
                     "visibleByDefault": false,
                     "idColumnName": "item_effective_call_number_typeid",
                     "source": {
                        "entityTypeId": "5c8315be-13f5-4df5-ae8b-086bae83484d",
                        "columnName": "call_number_type_name"
                      }
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
                     "name": "item_effective_library_code",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective library code",
                     "visibleByDefault": false
                },
                {
                     "name": "item_effective_library_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item effective library ID",
                     "visibleByDefault": false
                 },
                {
                     "name": "item_effective_library_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective library name",
                     "visibleByDefault": false,
                     "idColumnName": "item_effective_library_id",
                     "source": {
                        "entityTypeId": "cf9f5c11-e943-483c-913b-81d1e338accc",
                        "columnName": "loclibrary_name"
                     }
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
                     "name": "item_effective_location_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective location name",
                     "visibleByDefault": true,
                     "idColumnName": "item_effective_location_id",
                     "source": {
                        "entityTypeId": "a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b",
                        "columnName": "location_name"
                      }
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
                     "name": "item_permanent_location_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item permanent location name",
                     "visibleByDefault": false,
                     "idColumnName": "item_permanent_location_id",
                      "source": {
                        "entityTypeId": "a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b",
                        "columnName": "location_name"
                       }
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
                     "name": "item_temporary_location_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item temporary location name",
                     "visibleByDefault": false,
                     "idColumnName": "item_temporary_location_id",
                      "source": {
                        "entityTypeId": "a9d6305e-fdb4-4fc4-8a73-4a5f76d8410b",
                        "columnName": "location_name"
                      }
                },
                {
                     "name": "item_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Item updated date",
                     "visibleByDefault": false
                 }
             ],
              "defaultSort": [
                 {
                   "columnName": "item_effective_call_number_prefix",
                   "direction": "ASC"
                 },
                 {
                   "columnName": "item_effective_call_number_callNumber",
                   "direction": "ASC"
                 },
                 {
                   "columnName": "item_effective_call_number_suffix",
                   "direction": "ASC"
                 },
                 {
                   "columnName": "item_effective_call_number_copyNumber",
                   "direction": "ASC"
                 },
                 {
                   "columnName": "item_effective_location_name",
                   "direction": "ASC"
                 },
                 {
                    "columnName": "instance_title",
                    "direction": "ASC"
                 },
                 {
                    "columnName": "instance_primary_contributor",
                    "direction": "ASC"
                 },
                 {
                    "columnName": "id",
                    "direction": "ASC"
                 }
               ]
       }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
