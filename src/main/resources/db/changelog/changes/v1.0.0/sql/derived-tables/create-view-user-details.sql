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
    END AS preferred_contact_type
FROM src_users_users UserDetails
  LEFT JOIN src_users_groups patron_id_ref_data ON patron_id_ref_data.id = (UserDetails.jsonb ->> 'patronGroup')::uuid

