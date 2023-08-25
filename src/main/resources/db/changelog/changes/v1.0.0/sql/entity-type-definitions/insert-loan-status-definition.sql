INSERT INTO entity_type_definition (id, derived_table_name, definition)
     VALUES ('146dfba5-cdc9-45f5-a8a1-3fdc454c9ae2','drv_circulation_loan_status', '{
         "id": "146dfba5-cdc9-45f5-a8a1-3fdc454c9ae2",
         "name": "drv_loan_status",
         "labelAlias": "Loan status",
         "root": false,
         "private" : true,
         "columns": [
             {
                 "name": "loan_status",
                 "dataType":{
                     "dataType": "stringType"
                 },
                 "labelAlias": "Loan status",
                 "visibleByDefault": true
             }
         ]
 }');
