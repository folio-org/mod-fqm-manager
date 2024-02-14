INSERT INTO entity_type_definition (id, derived_table_name, definition)
    VALUES ('837f262e-2073-4a00-8bcc-4e4ce6e669b3','drv_organization_details', '{
             "id": "837f262e-2073-4a00-8bcc-4e4ce6e669b3",
             "name":"drv_organization_details",
             "private" : false,
             "fromClause" : "src_organizations AS org LEFT JOIN LATERAL jsonb_array_elements(org.jsonb->''accounts'') AS accounts(account_data) ON true",
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
                  "filterValueGetter": "${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''code''::text)",
                  "valueFunction": "${tenant_id}_mod_organizations_storage.f_unaccent(:value)",
                  "valueGetter": "org.jsonb->>''code''",
                  "visibleByDefault": false
                },
                {
                  "name": "name",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "filterValueGetter": "${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''name''::text)",
                  "valueFunction": "${tenant_id}_mod_organizations_storage.f_unaccent(:value)",
                  "valueGetter": "org.jsonb->>''name''",
                  "visibleByDefault": false
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
                  "filterValueGetter": "${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''status''::text)",
                  "valueFunction": "${tenant_id}_mod_organizations_storage.f_unaccent(:value)",
                  "visibleByDefault": false
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
                  "filterValueGetter": "${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''erpCode''::text)",
                  "valueFunction": "${tenant_id}_mod_organizations_storage.f_unaccent(:value)",
                  "valueGetter": "org.jsonb->>''erpCode''",
                  "visibleByDefault": false
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
                  "valueGetter": "org.jsonb->>''paymentMethod''",
                  "visibleByDefault": false
                },
                {
                  "name": "discount_percent",
                  "dataType": {
                    "dataType": "numberType"
                  },
                  "valueGetter": "org.jsonb->>''discountPercent''",
                  "visibleByDefault": false
                },
                {
                  "name": "claiming_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''claimingInterval''",
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
                  "valueGetter": "org.jsonb->>''exportToAccounting''",
                  "visibleByDefault": false
                },
                {
                  "name": "subscription_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''subscriptionInterval''",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_invoice_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''expectedInvoiceInterval''",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_receipt_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''expectedReceiptInterval''",
                  "visibleByDefault": false
                },
                {
                  "name": "renewal_activation_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''renewalActivationInterval''",
                  "visibleByDefault": false
                },
                {
                  "name": "expected_activation_interval",
                  "dataType": {
                    "dataType": "integerType"
                  },
                  "valueGetter": "org.jsonb->>''expectedActivationInterval''",
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
                  "valueGetter": "org.jsonb->>''vendorCurrencies''",
                  "visibleByDefault": false
                },
                {
                  "name": "tax_id",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "filterValueGetter": "${tenant_id}_mod_organizations_storage.f_unaccent(org.jsonb ->> ''taxId''::text)",
                  "valueFunction": "${tenant_id}_mod_organizations_storage.f_unaccent(:value)",
                  "valueGetter": "org.jsonb->>''taxId''",
                  "visibleByDefault": false
                },
                {
                  "name": "tax_percentage",
                  "dataType": {
                    "dataType": "numberType"
                  },
                  "valueGetter": "org.jsonb->>''taxPercentage''",
                  "visibleByDefault": false
                },
                {
                  "name": "account_number",
                  "dataType": {
                    "dataType": "stringType"
                  },
                  "valueGetter": "accounts.account_data->>''accountNo''",
                  "visibleByDefault": false
                },
                {
                  "name": "account_status",
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
                  "valueGetter": "accounts.account_data->>''accountStatus''",
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
                  "valueGetter": "(SELECT jsonb_agg(alias_data.jsonb->>''value'') FROM jsonb_array_elements(org.jsonb->''aliases'') AS alias_data(alias_data))",
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
                  "valueGetter": "ARRAY( SELECT jsonb_array_elements_text(org.jsonb->''organizationTypes'') as org_id)",
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
                  "valueGetter": "ARRAY( SELECT d.jsonb->>''name'' FROM jsonb_array_elements_text(org.jsonb->''organizationTypes'') as org_id JOIN src_organization_types d ON org_id = d.id::text)",
                  "visibleByDefault": false
                },
                {
                  "name": "id",
                  "dataType": {
                    "dataType": "rangedUUIDType"
                  },
                  "valueGetter": "org.id",
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
