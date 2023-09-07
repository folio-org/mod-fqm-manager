CREATE OR REPLACE VIEW drv_user_details
 AS
SELECT

        UserDetails.jsonb -> 'personal' ->> 'firstName' as first_name,
        UserDetails.jsonb -> 'personal' ->> 'lastName' as last_name,
        UserDetails.jsonb ->>'barcode' as barcode,
        UserDetails.jsonb ->>'username' as username,
        UserDetails.id as id,
        UserDetails.jsonb ->> 'externalSystemId' as external_system_id,
        UserDetails.jsonb ->> 'active' as active,
        UserDetails.jsonb -> 'personal' ->> 'email' as email,
        UserDetails.jsonb ->> 'createdDate' as created_date,
        UserDetails.jsonb ->> 'updatedDate' as updated_date,
        UserDetails.jsonb -> 'personal' ->> 'preferredFirstName' as preferred_first_name,
        UserDetails.jsonb -> 'personal' ->> 'middleName' as middle_name,
        UserDetails.jsonb -> 'personal' ->> 'phone' as phone,
        UserDetails.jsonb -> 'personal' ->> 'mobilePhone' as mobile_phone,
        UserDetails.jsonb -> 'personal' ->> 'dateOfBirth' as date_of_birth,
        UserDetails.jsonb ->> 'expirationDate'::text AS expiration_date,
        UserDetails.jsonb ->> 'enrollmentDate'::text AS enrollment_date,
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
              CASE
                  WHEN (( SELECT array_agg(add_id.value ->> 'primaryAddress'::text) FILTER (WHERE (add_id.value ->> 'primaryAddress'::text) IS NOT NULL) AS array_agg
                     FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value))) = ARRAY['true'::text] THEN array_cat(array_cat(array_cat(array_cat(array_cat(( SELECT array_agg(subquery.city) FILTER (WHERE subquery.city IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'city'::text AS city,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1), ( SELECT array_agg(subquery.region) FILTER (WHERE subquery.region IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'region'::text AS region,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1)), ( SELECT array_agg(subquery.countryid) FILTER (WHERE subquery.countryid IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'countryId'::text AS countryid,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1)), ( SELECT array_agg(subquery.postalcode) FILTER (WHERE subquery.postalcode IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'postalCode'::text AS postalcode,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1)), ( SELECT array_agg(subquery.addressline1) FILTER (WHERE subquery.addressline1 IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'addressLine1'::text AS addressline1,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1)), ( SELECT array_agg(subquery.addressline2) FILTER (WHERE subquery.addressline2 IS NOT NULL) AS array_agg
                     FROM ( SELECT add_id.value ->> 'addressLine2'::text AS addressline2,
                              row_number() OVER () AS row_num
                             FROM jsonb_array_elements((userdetails.jsonb -> 'personal'::text) -> 'addresses'::text) add_id(value)) subquery
                    WHERE subquery.row_num = 1))
                  ELSE NULL::text[]
              END AS primaryaddress,
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
                   JOIN src_users_addresstype a ON (add_id.value ->> 'addressTypeId'::text) = a.id::text) AS address_type_names

FROM src_users_users UserDetails
  LEFT JOIN src_users_groups patron_id_ref_data ON patron_id_ref_data.id = (UserDetails.jsonb ->> 'patronGroup')::uuid

