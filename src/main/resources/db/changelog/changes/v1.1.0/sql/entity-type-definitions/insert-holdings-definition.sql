INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('8418e512-feac-4a6a-a56d-9006aab31e33','drv_holdings_record_details', '{
             "id": "8418e512-feac-4a6a-a56d-9006aab31e33",
             "name":"drv_holdings_record_details",
             "private" : false,
             "fromClause" : "src_inventory_holdings_record hrd",
             "columns": [
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
                     "name": "holdings_statistical_code_ids",
                      "dataType":{
                        "dataType":"arrayType",
                        "itemDataType": {
                          "dataType": "rangedUUIDType"
                        }
                      },
                     "valueGetter": "( SELECT array_agg(record.value::text) FILTER (WHERE (record.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(hrd.jsonb -> ''statisticalCodeIds''::text) record(value))",
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
                      "filterValueGetter": "\"left\"(lower(hrd.jsonb ->> ''discoverySuppress''::text), 600)",
                      "valueFunction": "\"left\"(lower(:value), 600)",
                      "visibleByDefault": true
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
