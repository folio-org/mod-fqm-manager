INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('837f262e-2073-4a00-8bcc-4e4ce6e669b3','drv_organization_details', '{
             "id": "837f262e-2073-4a00-8bcc-4e4ce6e669b3",
             "name":"drv_organization_details",
             "private" : false,
             "fromClause" : "src_organizations as org",
             "columns" : [
                {
                  "name": "last_updated",
                  "dataType": {
                    "dataType": "dateType"
                  },
                  "valueGetter": "org.jsonb->''metadata''->>''updatedDate''",
                  "visibleByDefault": false
                },
                {
                  "name": "code",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "org.jsonb->>''code''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''code''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": true
                },
                {
                  "name": "name",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "org.jsonb->>''name''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''name''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": true
                },
                {
                  "name": "organization_status",
                  "values": [
                    {
                      "label": "Active",
                      "value": "active"
                    },
                    {
                      "label": "Inactive",
                      "value": "inactive"
                    },
                    {
                      "label": "Pending",
                      "value": "pending"
                    }
                  ],
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "org.jsonb->>''status''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''status''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": true
                },
                {
                  "name": "description",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "org.jsonb->>''description''",
                  "visibleByDefault": false
                },
                {
                  "name": "accounting_code",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "org.jsonb->>''erpCode''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''erpCode''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": false
                },
                {
                  "name": "alias",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "stringType"
                    }
                  },
                  "valueGetter": "(SELECT array_agg(alias) FROM jsonb_array_elements_text(org.jsonb -> ''aliases'') AS alias)",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''aliases''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": false
                },
                {
                  "name": "organization_type_ids",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "rangedUUIDType"
                    }
                  },
                  "valueGetter": "( SELECT array_agg(record.value::text) FILTER (WHERE (record.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(org.jsonb -> ''organizationTypes''::text) record(value))",
                  "visibleByDefault": false
                },
                {
                  "name": "organization_type_name",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "stringType"
                    }
                  },
                  "idColumnName": "organization_type_ids",
                  "source": {
                    "entityTypeId": "6b335e41-2654-4e2a-9b4e-c6930b330ccc",
                    "columnName": "organization_types_name"
                  },
                   "valueGetter": "( SELECT array_agg(a.jsonb ->> ''name''::text) FILTER (WHERE (a.jsonb ->> ''name''::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text((org.jsonb -> ''organizationTypes''::text)) record(value) JOIN src_organization_types a ON (record.value::text) = a.id::text)",
                   "visibleByDefault": false
                },
                {
                  "name": "id",
                  "dataType": {
                    "dataType": "rangedUUIDType"
                  },
                  "valueGetter": "org.id",
                  "isIdColumn": true,
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
