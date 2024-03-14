INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('837f262e-2073-4a00-8bcc-4e4ce6e669b3','drv_organization_details', '{
              "id": "837f262e-2073-4a00-8bcc-4e4ce6e669b3",
              "name": "drv_organization_details",
              "private": false,
              "fromClause": "src_organizations as org",
              "columns": [
                {
                  "name": "last_updated",
                  "dataType": {
                    "dataType": "dateType"
                  },
                  "queryable": true,
                  "valueGetter": "org.jsonb->''metadata''->>''updatedDate''",
                  "visibleByDefault": false
                },
                {
                  "name": "code",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "queryable": true,
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
                  "queryable": true,
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
                  "queryable": true,
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
                  "queryable": true,
                  "valueGetter": "org.jsonb->>''description''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb->>''description''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": false
                },
                {
                  "name": "accounting_code",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "queryable": true,
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
                       "dataType": "objectType",
                       "properties": [
                         {
                           "name": "value",
                           "property": "value",
                           "dataType": {"dataType": "stringType"},
                           "queryable": false,
                           "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''value'')) FROM jsonb_array_elements(org.jsonb -> ''aliases'') AS elems)",
                           "valueFunction": "lower(:value)"
                         },
                        {
                           "name": "description",
                           "property": "description",
                           "dataType": {"dataType": "stringType"},
                           "queryable": false,
                           "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''description'')) FROM jsonb_array_elements(org.jsonb -> ''aliases'') AS elems)",
                           "valueFunction": "lower(:value)"
                         }
                       ]
                     }
                   },
                   "valueGetter": "org.jsonb ->> ''aliases''",
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
                  "queryable": false,
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
                  "queryable": false,
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
                  "queryable": true,
                  "valueGetter": "org.id",
                  "isIdColumn": true,
                  "visibleByDefault": true
                },
                {
                  "name": "payment_method",
                  "values": [
                    {
                      "label": "Credit Card",
                      "value": "Credit Card"
                    },
                    {
                      "label": "Cash",
                      "value": "Cash"
                    },
                    {
                      "label": "Physical Check",
                      "value": "Physical Check"
                    },
                    {
                      "label": "EFT",
                      "value": "EFT"
                    },
                    {
                      "label": "Deposit Account",
                      "value": "Deposit Account"
                    }
                  ],
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "queryable": true,
                  "valueGetter": "org.jsonb->>''paymentMethod''",
                  "visibleByDefault": false
                },
                {
                  "name": "discount_percent",
                  "dataType": {
                    "dataType": "numberType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''discountPercent'')::float",
                  "valueFunction": "(:value)::float",
                  "visibleByDefault": false
                },
                {
                  "name": "claiming_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''claimingInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "export_to_accounting",
                  "values": [
                    {
                      "label": "True",
                      "value": "true"
                    },
                    {
                      "label": "False",
                      "value": "false"
                    }
                  ],
                  "dataType": {
                    "dataType": "booleanType"
                  },
                  "queryable": true,
                  "valueGetter": "org.jsonb->>''exportToAccounting''",
                  "visibleByDefault": false
                },
                {
                  "name": "subscription_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''subscriptionInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_invoice_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''expectedInvoiceInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_receipt_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''expectedReceiptInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "renewal_activation_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''renewalActivationInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_activation_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''expectedActivationInterval'')::integer",
                  "valueFunction": "(:value)::integer",
                  "visibleByDefault": false
                },
                {
                  "name": "tax_id",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "queryable": true,
                  "valueGetter": "org.jsonb->>''taxId''",
                  "filterValueGetter": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''taxId''::text))",
                  "valueFunction": "lower(${tenant_id}_mod_organizations_storage.f_unaccent(:value))",
                  "visibleByDefault": false
                },
                {
                  "name": "tax_percentage",
                  "dataType": {
                    "dataType": "numberType"
                  },
                  "queryable": true,
                  "valueGetter": "(org.jsonb->''taxPercentage'')::float",
                  "valueFunction": "(:value)::float",
                  "visibleByDefault": false
                },
                {
                  "name": "vendor_currencies",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "stringType"
                    }
                  },
                  "queryable": false,
                  "valueGetter": "(SELECT array_agg(vendorCurrency) FROM jsonb_array_elements_text(org.jsonb->''vendorCurrencies'') AS vendorCurrency)",
                  "visibleByDefault": false
                },
                {
                  "name": "agreements",
                  "dataType": {
                    "dataType": "arrayType",
                    "itemDataType": {
                      "dataType": "objectType",
                      "properties": [
                        {
                          "name": "name",
                          "property": "name",
                          "dataType": {
                            "dataType": "stringType"
                          },
                          "queryable": false,
                          "valueGetter": "( SELECT array_agg(elems.value ->> ''name'') FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''name'')) FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "valueFunction": "lower(:value)"
                        },
                        {
                          "name": "discount",
                          "property": "discount",
                          "dataType": {
                            "dataType": "numberType"
                          },
                          "queryable": false,
                          "valueGetter": "( SELECT array_agg(elems.value -> ''discount'')::float FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "valueFunction": "(:value)::float"
                        },
                        {
                          "name": "referenceUrl",
                          "property": "referenceUrl",
                          "dataType": {
                            "dataType": "stringType"
                          },
                          "queryable": false,
                          "valueGetter": "( SELECT array_agg(elems.value ->> ''referenceUrl'') FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''referenceUrl'')) FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "valueFunction": "lower(:value)"
                        },
                        {
                          "name": "notes",
                          "property": "notes",
                          "dataType": {
                            "dataType": "stringType"
                          },
                          "queryable": false,
                          "valueGetter": "( SELECT array_agg(elems.value ->> ''notes'') FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''notes'')) FROM jsonb_array_elements(org.jsonb -> ''agreements'') AS elems)",
                          "valueFunction": "lower(:value)"
                        }
                      ]
                    }
                  },
                  "valueGetter": "org.jsonb ->> ''agreements''",
                  "visibleByDefault": false
                },
              {
                "name": "acqunit_ids",
                "dataType": {
                  "dataType": "arrayType",
                  "itemDataType": {
                    "dataType": "rangedUUIDType"
                  }
                },
                "queryable": false,
                "valueGetter": "( SELECT array_agg(acq_id.value::text) FILTER (WHERE (acq_id.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(org.jsonb -> ''acqUnitIds''::text) acq_id(value))",
                "filterValueGetter": "( SELECT array_agg(lower(acq_id.value::text)) FILTER (WHERE (acq_id.value::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text(org.jsonb -> ''acqUnitIds''::text) acq_id(value))",
                "valueFunction": "lower(:value)",
                "visibleByDefault": false
              },
              {
                "name": "acquisition_unit",
                "dataType": {
                  "dataType": "arrayType",
                  "itemDataType": {
                    "dataType": "stringType"
                  }
                },
                "queryable": false,
                "valueGetter": "( SELECT array_agg(acq_unit.jsonb ->> ''name''::text) FILTER (WHERE (acq_unit.jsonb ->> ''name''::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text((org.jsonb -> ''acqUnitIds''::text)) record(value) JOIN src_acquisitions_unit acq_unit ON lower(record.value::text) = acq_unit.id::text)",
                "filterValueGetter": "( SELECT array_agg(lower(acq_unit.jsonb ->> ''name''::text)) FILTER (WHERE (acq_unit.jsonb ->> ''name''::text) IS NOT NULL) AS array_agg FROM jsonb_array_elements_text((org.jsonb -> ''acqUnitIds''::text)) record(value) JOIN src_acquisitions_unit acq_unit ON (record.value::text) = acq_unit.id::text)",
                "valueFunction": "lower(:value)",
                "visibleByDefault": false,
                "idColumnName": "acqunit_ids",
                "source": {
                  "entityTypeId": "cc51f042-03e2-43d1-b1d6-11aa6a39bc78",
                  "columnName": "acquisitions_name"
                  }
                },
              {
                "name": "accounts",
                "dataType": {
                  "dataType": "arrayType",
                  "itemDataType": {
                    "dataType": "objectType",
                    "properties": [
                      {
                        "name": "name",
                        "property": "name",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''name'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                      {
                        "name": "notes",
                        "property": "notes",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''notes'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                      {
                        "name": "accountNo",
                        "property": "accountNo",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''accountNo'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                      {
                        "name": "libraryCode",
                        "property": "libraryCode",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''libraryCode'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                      {
                        "name": "libraryEdiCode",
                        "property": "libraryEdiCode",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''libraryEdiCode'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                     {
                        "name": "contactInfo",
                        "property": "contactInfo",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''contactInfo'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                     {
                        "name": "appSystemNo",
                        "property": "appSystemNo",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''appSystemNo'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                     {
                        "name": "description",
                        "property": "description",
                        "dataType": { "dataType": "stringType" },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''description'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                        "valueFunction": "lower(:value)"
                      },
                      {
                         "name": "accountStatus",
                         "property": "accountStatus",
                         "dataType": {"dataType": "stringType"},
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
                         "queryable": false,
                         "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''accountStatus'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                         "valueFunction": "lower(:value)"
                       },
                       {
                         "name": "paymentMethod",
                         "property": "paymentMethod",
                         "dataType": {"dataType": "stringType"},
                         "values": [
                            {
                               "label": "Credit Card",
                               "value": "Credit Card"
                             },
                             {
                               "label": "Cash",
                               "value": "Cash"
                             },
                             {
                               "label": "Physical Check",
                               "value": "Physical Check"
                             },
                             {
                               "label": "EFT",
                               "value": "EFT"
                             },
                             {
                               "label": "Deposit Account",
                               "value": "Deposit Account"
                             }
                         ],
                         "queryable": false,
                         "filterValueGetter": "( SELECT array_agg(lower(elems.value ->> ''paymentMethod'')) FROM jsonb_array_elements(org.jsonb -> ''accounts'') AS elems)",
                         "valueFunction": "lower(:value)"
                       },
                      {
                        "name": "acqUnitIds",
                        "property": "acqUnitIds",
                        "dataType": {
                          "dataType": "arrayType",
                          "itemDataType": { "dataType": "rangedUUIDType" }
                        },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(elems#>>''{}'')) FROM jsonb_array_elements(org.jsonb->''accounts'') AS acc_obj, jsonb_array_elements(acc_obj->''acqUnitIds'') as elems )"
                      },
                      {
                        "name": "acquisition_unit",
                        "property": "acquisitionUnit",
                        "dataType": {
                          "dataType": "arrayType",
                          "itemDataType": { "dataType": "stringType" }
                        },
                        "queryable": false,
                        "filterValueGetter": "( SELECT array_agg(lower(au.jsonb->>''name'')) FROM jsonb_array_elements(org.jsonb->''accounts'') as acc, jsonb_array_elements(acc->''acqUnitIds'') as acdId JOIN src_acquisitions_unit au ON au.id = (acdId#>>''{}'')::uuid )"
                      }
                    ]
                  }
                },
                "valueGetter": "(SELECT jsonb_agg(accounts || jsonb_build_object(''acquisitionUnit'', COALESCE(( SELECT array_agg(au.jsonb->>''name'') FROM jsonb_array_elements(accounts->''acqUnitIds'') as acdId JOIN src_acquisitions_unit au on au.id = (acdId#>>''{}'')::uuid ), array []::text[])))::text FROM jsonb_array_elements(org.jsonb->''accounts'') as accounts)",
                "visibleByDefault": false
             }
              ],
              "defaultSort": [
                {
                  "columnName": "id",
                  "direction": "ASC"
                }
              ]
            }') ON CONFLICT (id) DO UPDATE SET derived_table_name = EXCLUDED.derived_table_name, definition = EXCLUDED.definition;
