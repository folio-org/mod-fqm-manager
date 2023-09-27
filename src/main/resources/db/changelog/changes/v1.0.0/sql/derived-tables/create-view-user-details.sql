CREATE OR REPLACE VIEW drv_user_details
AS
SELECT
        userDetails.jsonb -> 'personal' ->> 'firstName' as first_name,
        userDetails.jsonb -> 'personal' ->> 'lastName' as last_name,
        userDetails.jsonb ->>'barcode' as barcode,
        userDetails.jsonb ->>'username' as username,
        userDetails.id as id,
        userDetails.jsonb ->> 'externalSystemId' as external_system_id,
        userDetails.jsonb ->> 'active' as active,
        userDetails.jsonb -> 'personal' ->> 'email' as email,
        userDetails.jsonb ->> 'createdDate' as created_date,
        userDetails.jsonb ->> 'updatedDate' as updated_date,
        userDetails.jsonb -> 'personal' ->> 'preferredFirstName' as preferred_first_name,
        userDetails.jsonb -> 'personal' ->> 'middleName' as middle_name,
        userDetails.jsonb -> 'personal' ->> 'phone' as phone,
        userDetails.jsonb -> 'personal' ->> 'mobilePhone' as mobile_phone,
        userDetails.jsonb -> 'personal' ->> 'dateOfBirth' as date_of_birth,
        userDetails.jsonb ->> 'expirationDate'::text AS expiration_date,
        userDetails.jsonb ->> 'enrollmentDate'::text AS enrollment_date,
        patron_id_ref_data.jsonb ->> 'group'::text AS user_patron_group,
        patron_id_ref_data.id::text AS user_patron_group_id,
        UserDetails.jsonb -> 'personal' ->> 'preferredContactTypeId' as preferred_contact_type_id,
        CASE UserDetails.jsonb -> 'personal' ->> 'preferredContactTypeId'
          WHEN '001' THEN 'mail'
          WHEN '002' THEN 'email'
          WHEN '003' THEN 'text'
          WHEN '004' THEN 'phone'
          WHEN '005' THEN 'mobile'
          ELSE 'unknown'
        END AS preferred_contact_type,
        concat_ws(', ',
        		  NULLIF((SELECT subquery.city
        				  FROM (
        						SELECT add_id.value ->> 'city'::text AS city,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), ''),
        		  NULLIF((SELECT subquery.region
        				  FROM (
        						SELECT add_id.value ->> 'region'::text AS region,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), ''),
        		  NULLIF((SELECT subquery.countryId
        				  FROM (
        						SELECT add_id.value ->> 'countryId'::text AS countryId,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), ''),
        		  NULLIF((SELECT subquery.postalCode
        				  FROM (
        						SELECT add_id.value ->> 'postalCode'::text AS postalCode,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), ''),
        		  NULLIF((SELECT subquery.addressLine1
        				  FROM (
        						SELECT add_id.value ->> 'addressLine1'::text AS addressLine1,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), ''),
        		  NULLIF((SELECT subquery.addressLine2
        				  FROM (
        						SELECT add_id.value ->> 'addressLine2'::text AS addressLine2,
        						row_number() OVER (ORDER BY (add_id.value ->> 'primaryAddress'::text)) AS row_num
        						FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
        				  WHERE subquery.row_num = 1), '')
        ) AS primary_address,
        ( SELECT array_agg(add_id.value ->> 'city'::text) FILTER (WHERE (add_id.value ->> 'city'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS cities,
        ( SELECT array_agg(add_id.value ->> 'region'::text) FILTER (WHERE (add_id.value ->> 'region'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS regions,
        ( SELECT array_agg(add_id.value ->> 'countryId'::text) FILTER (WHERE (add_id.value ->> 'countryId'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS country_ids,
        ( SELECT array_agg(add_id.value ->> 'postalCode'::text) FILTER (WHERE (add_id.value ->> 'postalCode'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS postal_codes,
        ( SELECT array_agg(add_id.value ->> 'addressLine1'::text) FILTER (WHERE (add_id.value ->> 'addressLine1'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS address_line1,
        ( SELECT array_agg(add_id.value ->> 'addressLine2'::text) FILTER (WHERE (add_id.value ->> 'addressLine2'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS address_line2,
        ( SELECT array_agg(add_id.value ->> 'addressTypeId'::text) FILTER (WHERE (add_id.value ->> 'addressTypeId'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) AS address_ids,
        ( SELECT array_agg(a.jsonb ->> 'addressType'::text) FILTER (WHERE (a.jsonb ->> 'addressType'::text) IS NOT NULL) AS array_agg
               FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)
                 JOIN src_users_addresstype a ON (add_id.value ->> 'addressTypeId'::text) = a.id::text) AS address_type_names,
        array_agg(temp_departments.id::text) FILTER (where temp_departments.id is not null) as department_ids,
        array_agg(temp_departments.jsonb ->> 'name') FILTER (where temp_departments.jsonb ->> 'name' is not null) as department_names
FROM src_users_users userdetails
        LEFT JOIN src_users_groups patron_id_ref_data ON patron_id_ref_data.id = ((userdetails.jsonb ->> 'patronGroup'::text)::uuid)
        LEFT JOIN src_users_departments as temp_departments ON userdetails.jsonb -> 'departments' ?? temp_departments.id::text
GROUP BY userdetails.id, userdetails.jsonb, patron_id_ref_data.id, patron_id_ref_data.jsonb

