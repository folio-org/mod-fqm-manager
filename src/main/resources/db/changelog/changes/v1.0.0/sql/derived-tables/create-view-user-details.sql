CREATE OR REPLACE VIEW drv_user_details
 AS
SELECT

        UserDetails.jsonb -> 'personal' ->> 'firstName' as FirstName,
        UserDetails.jsonb -> 'personal' ->> 'lastName' as LastName,
        UserDetails.jsonb ->>'barcode' as Barcode,
        UserDetails.jsonb ->>'username' as Username,
        UserDetails.id as id,
        UserDetails.jsonb ->> 'externalSystemId' as ExternalSystemId,
        UserDetails.jsonb ->> 'active' as Active,
        UserDetails.jsonb -> 'personal' ->> 'email' as Email,
        UserDetails.jsonb ->> 'createdDate' as CreatedDate,
        UserDetails.jsonb ->> 'updatedDate' as UpdatedDate,
        UserDetails.jsonb -> 'personal' ->> 'preferredFirstName' as PreferredFirstName,
        UserDetails.jsonb -> 'personal' ->> 'middleName' as MiddleName,
        UserDetails.jsonb -> 'personal' ->> 'phone' as Phone,
        UserDetails.jsonb -> 'personal' ->> 'mobilePhone' as Mobilephone,
        UserDetails.jsonb -> 'personal' ->> 'dateOfBirth' as DateOfBirth,
        UserDetails.jsonb ->> 'expirationDate'::text AS Expiration_date,
        patron_id_ref_data.jsonb ->> 'group'::text AS user_patron_group,
        patron_id_ref_data.id::text AS user_patron_group_id,
        UserDetails.jsonb -> 'personal' ->> 'preferredContactTypeId' as preferredContactTypeId,
        CASE UserDetails.jsonb -> 'personal' ->> 'preferredContactTypeId'
        WHEN '001' THEN 'mail'
        WHEN '002' THEN 'email'
        WHEN '003' THEN 'text'
        WHEN '004' THEN 'phone'
        WHEN '005' THEN 'mobile'
        ELSE 'unknown'
    END AS PreferredContactType
FROM src_users_users UserDetails
  LEFT JOIN src_users_groups patron_id_ref_data ON patron_id_ref_data.id = (UserDetails.jsonb ->> 'patronGroup')::uuid

