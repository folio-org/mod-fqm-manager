INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('8418e512-feac-4a6a-a56d-9006aab31e33','drv_holdings_record_details', '{
             "id": "8418e512-feac-4a6a-a56d-9006aab31e33",
             "name":"drv_holdings_record_details",
             "private" : false,
             "fromClause" : "src_inventory_holdings_record hrd LEFT JOIN src_inventory_location effective_location ON effective_location.id = hrd.effectivelocationid LEFT JOIN src_inventory_loclibrary effective_library ON effective_library.id = effective_location.libraryid LEFT JOIN src_inventory_location permanent_location ON permanent_location.id = hrd.permanentlocationid LEFT JOIN src_inventory_location temporary_location ON temporary_location.id = hrd.temporarylocationid",
             "columns": [
                 {
                   "name": "holdings_effective_location",
                   "dataType": {
                     "dataType": "stringType"
                   },
                   "valueSourceApi": {
                     "path": "locations",
                     "valueJsonPath": "$.locations.*.id",
                     "labelJsonPath": "$.locations.*.name"
                   },
                   "source": {
                     "columnName": "holdings_effective_location",
                     "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                   },
                   "idColumnName": "holdings_effective_location_id",
                   "valueGetter": "effective_location.jsonb ->> ''name''",
                   "visibleByDefault": false
                 },
                 {
                   "name": "holdings_effective_location_id",
                   "dataType": {
                     "dataType": "rangedUUIDType"
                   },
                   "valueSourceApi": {
                     "path": "locations",
                     "valueJsonPath": "$.locations.*.id",
                     "labelJsonPath": "$.locations.*.id"
                   },
                   "source": {
                     "columnName": "holdings_effective_location_id",
                     "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                   },
                   "valueGetter": "hrd.effectivelocationid",
                   "visibleByDefault": false
                 },
                 {
                   "name": "holdings_effective_library_code",
                   "dataType": {
                     "dataType": "stringType"
                   },
                   "valueSourceApi": {
                     "path": "location-units/libraries",
                     "valueJsonPath": "$.loclibs.*.id",
                     "labelJsonPath": "$.loclibs.*.code"
                   },
                   "source": {
                     "columnName": "holdings_effective_library_code",
                     "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                   },
                   "idColumnName": "holdings_effective_library_id",
                   "valueGetter": "effective_library.jsonb ->> ''code''",
                   "visibleByDefault": false
                 },
                 {
                   "name": "holdings_effective_library_name",
                   "dataType": {
                     "dataType": "stringType"
                   },
                   "valueSourceApi": {
                     "path": "location-units/libraries",
                     "valueJsonPath": "$.loclibs.*.id",
                     "labelJsonPath": "$.loclibs.*.name"
                   },
                   "source": {
                     "columnName": "holdings_effective_library_name",
                     "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                   },
                   "idColumnName": "holdings_effective_library_id",
                   "valueGetter": "effective_library.jsonb ->> ''name''",
                   "visibleByDefault": false
                 },
                 {
                   "name": "holdings_effective_library_id",
                   "dataType": {
                     "dataType": "rangedUUIDType"
                   },
                   "valueSourceApi": {
                     "path": "location-units/libraries",
                     "valueJsonPath": "$.loclibs.*.id",
                     "labelJsonPath": "$.loclibs.*.id"
                   },
                   "source": {
                     "columnName": "holdings_effective_library_code_id",
                     "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                   },
                   "valueGetter": "effective_library.id",
                   "visibleByDefault": false
                 },
                 {
                     "name": "holdings_hrid",
                     "dataType": {
                         "dataType":"stringType"
                       },
                     "valueGetter": "hrd.jsonb ->> ''hrid''",
                     "filterValueGetter": "lower(${tenant_id}_mod_inventory_storage.f_unaccent(hrd.jsonb ->> ''hrid''::text))",
                     "valueFunction": "lower(${tenant_id}_mod_inventory_storage.f_unaccent(:value))",
                     "visibleByDefault": true
                 },
                 {
                     "name": "id",
                     "dataType": {
                         "dataType":"rangedUUIDType"
                       },
                     "valueGetter": "hrd.id",
                     "isIdColumn": true,
                     "visibleByDefault": true
                 },
                 {
                     "name": "holdings_permanent_location",
                     "dataType": {
                         "dataType": "stringType"
                     },
                     "valueSourceApi": {
                       "path": "locations",
                       "valueJsonPath": "$.locations.*.id",
                       "labelJsonPath": "$.locations.*.name"
                     },
                     "source": {
                       "columnName": "holdings_permanent_location",
                       "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                     },
                     "idColumnName": "holdings_permanent_location_id",
                     "valueGetter": "permanent_location.jsonb ->> ''name''",
                     "visibleByDefault": false
                 },
                 {
                     "name": "holdings_permanent_location_id",
                     "dataType": {
                         "dataType": "rangedUUIDType"
                     },
                     "valueSourceApi": {
                       "path": "locations",
                       "valueJsonPath": "$.locations.*.id",
                       "labelJsonPath": "$.locations.*.id"
                     },
                     "source": {
                       "columnName": "holdings_permanent_location_id",
                       "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                     },
                     "valueGetter": "hrd.permanentlocationid",
                     "visibleByDefault": false
                 },
                 {
                     "name": "holdings_statistical_code_ids",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "rangedUUIDType"
                        }
                      },
                     "valueGetter": "( SELECT array_agg(record.value::text) FILTER (WHERE (record.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(hrd.jsonb -> ''statisticalCodeIds''::text) record(value))",
                     "filterValueGetter": "( SELECT array_agg(lower(record.value::text)) FILTER (WHERE (record.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(hrd.jsonb -> ''statisticalCodeIds''::text) record(value))",
                     "valueFunction": "lower(:value)",
                     "visibleByDefault": false
                 },
                 {
                      "name": "holdings_statistical_codes",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "stringType"
                        }
                      },
                      "idColumnName": "holdings_statistical_code_ids",
                      "source": {
                        "entityTypeId": "d2da8cc7-9171-4d3e-8aba-4da286eb5f1c",
                        "columnName": "statistical_code"
                      },
                      "valueGetter": "( SELECT array_agg(statcode.statistical_code) FILTER (WHERE (statcode.statistical_code) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text((hrd.jsonb -> ''statisticalCodeIds''::text)) record(value) JOIN drv_inventory_statistical_code_full statcode ON (record.value::text) = statcode.id::text)",
                      "filterValueGetter": "( SELECT array_agg(lower(statcode.statistical_code)) FILTER (WHERE (statcode.statistical_code) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text((hrd.jsonb -> ''statisticalCodeIds''::text)) record(value) JOIN drv_inventory_statistical_code_full statcode ON (record.value::text) = statcode.id::text)",
                      "valueFunction": "lower(:value)",
                      "visibleByDefault": true
                 },
                 {
                      "name": "holdings_suppress_from_discovery",
                      "dataType": {
                          "dataType":"booleanType"
                        },
                      "values": [
                        {
                          "value": "true",
                          "label": "True"
                        },
                        {
                          "value": "false",
                          "label": "False"
                        }
                      ],
                      "valueGetter": "hrd.jsonb ->> ''discoverySuppress''",
                      "filterValueGetter": "COALESCE(\"left\"(lower(hrd.jsonb ->> ''discoverySuppress''::text), 600), ''false'')",
                      "valueFunction": "\"left\"(lower(:value), 600)",
                      "visibleByDefault": true
                 },
                 {
                    "name": "holdings_temporary_location",
                    "dataType": {
                      "dataType": "stringType"
                    },
                    "valueSourceApi": {
                      "path": "locations",
                      "valueJsonPath": "$.locations.*.id",
                      "labelJsonPath": "$.locations.*.name"
                    },
                    "source": {
                      "columnName": "holdings_temporary_location",
                      "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                    },
                    "idColumnName": "holdings_temporary_location_id",
                    "valueGetter": "temporary_location.jsonb ->> ''name''",
                    "visibleByDefault": false
                 },
                 {
                    "name": "holdings_temporary_location_id",
                    "dataType": {
                      "dataType": "rangedUUIDType"
                    },
                    "valueSourceApi": {
                      "path": "locations",
                      "valueJsonPath": "$.locations.*.id",
                      "labelJsonPath": "$.locations.*.id"
                    },
                    "source": {
                      "columnName": "holdings_temporary_location_id",
                      "entityTypeId": "8418e512-feac-4a6a-a56d-9006aab31e33"
                    },
                    "valueGetter": "hrd.temporarylocationid",
                    "visibleByDefault": false
                 },
                 {
                       "name": "instance_id",
                       "dataType": {
                           "dataType":"rangedUUIDType"
                         },
                       "valueGetter": "hrd.instanceid",
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
