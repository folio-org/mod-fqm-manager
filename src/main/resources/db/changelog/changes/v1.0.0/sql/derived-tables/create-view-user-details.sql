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
        array_agg(temp_departments.id::text) FILTER (where temp_departments.id is not null) as department_ids,
        array_agg(temp_departments.jsonb ->> 'name') FILTER (where temp_departments.jsonb ->> 'name' is not null) as department_names
FROM src_users_users userdetails
        LEFT JOIN src_users_groups patron_id_ref_data ON patron_id_ref_data.id = ((userdetails.jsonb ->> 'patronGroup'::text)::uuid)
        LEFT JOIN src_users_departments as temp_departments ON userdetails.jsonb -> 'departments' ?? temp_departments.id::text
GROUP BY userdetails.id, userdetails.jsonb, patron_id_ref_data.id, patron_id_ref_data.jsonb

