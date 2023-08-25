INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('097a6f96-edd0-11ed-a05b-0242ac120003','drv_item_callnumber_location', '{
             "id": "097a6f96-edd0-11ed-a05b-0242ac120003",
             "name":"drv_item_callnumber_location",
             "labelAlias" : "Item, Call number and location",
             "private" : true,
             "columns": [
                 {
                     "name": "id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item ID",
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
                     "name": "item_effective_call_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Effective call number",
                     "visibleByDefault": true
                 },
                 {
                     "name": "item_effective_call_number_typeid",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Effective call number typeId",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_effective_call_number_type_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Effective call number type name",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_level_call_number",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Call number",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_level_call_number_typeid",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Call number typeId",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_level_call_number_type_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Call number type name",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_holdings_record_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Holdings record ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_status",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item status",
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
                     "name": "item_barcode",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item barcode",
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
                     "name": "item_updated_date",
                     "dataType":{
                       "dataType":"dateType"
                     },
                     "labelAlias": "Item updated date",
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
                     "name": "item_effective_location_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item location name",
                     "visibleByDefault": true
                 },
                 {
                     "name": "item_effective_library_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Item effective library id",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_effective_library_name",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Item effective library name",
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
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_material_type_id",
                     "dataType":{
                       "dataType":"rangedUUIDType"
                     },
                     "labelAlias": "Material ID",
                     "visibleByDefault": false
                 },
                 {
                     "name": "item_material_type",
                     "dataType":{
                       "dataType":"stringType"
                     },
                     "labelAlias": "Material type",
                     "visibleByDefault": false,
                     "idColumnName": "item_material_type_id",
                     "source": {
                       "entityTypeId": "917ea5c8-cafe-4fa6-a942-e2388a88c6f6",
                       "columnName": "material_type_name"
                     }
                 }
             ]
         }');
