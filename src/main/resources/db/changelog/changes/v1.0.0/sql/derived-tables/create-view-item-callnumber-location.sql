CREATE OR REPLACE VIEW drv_item_callnumber_location
 AS
 SELECT src_inventory_item.id,
    src_inventory_item.holdingsrecordid AS item_holdings_record_id,
    src_inventory_item.jsonb ->> 'hrid'::text AS item_hrid,
    src_inventory_item.jsonb ->> 'itemLevelCallNumber'::text AS item_level_call_number,
    src_inventory_item.jsonb ->> 'itemLevelCallNumberTypeId'::text AS item_level_call_number_typeid,
    call_item_number_type_ref_data.item_level_call_number_type_name,
        concat_ws(', ',
            NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'prefix'::text, ''),
            NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text, ''),
            NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text, ''),
            NULLIF(src_inventory_item.jsonb ->> 'copyNumber'::text, '')
            ) AS item_effective_call_number,
    (src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'typeId'::text AS item_effective_call_number_typeId,
    call_number_type_ref_data.item_effective_call_number_type_name,
    loclib_ref_data.id AS item_effective_library_id,
    loclib_ref_data.item_effective_library_name,
    loclib_ref_data.item_effective_library_code,
    "left"(lower(f_unaccent((src_inventory_item.jsonb -> 'status'::text) ->> 'name'::text)), 600) AS item_status,
    src_inventory_item.jsonb ->> 'copyNumber'::text AS item_copy_number,
    src_inventory_item.jsonb ->> 'barcode'::text AS item_barcode,
    (src_inventory_item.jsonb -> 'metadata') ->> 'createdDate'::text AS item_created_date,
    (src_inventory_item.jsonb -> 'metadata') ->> 'updatedDate'::text AS item_updated_date,
	  src_inventory_item.effectivelocationid AS item_effective_location_id,
    effective_location_ref_data.locationname AS item_effective_location_name,
    src_inventory_item.permanentlocationid AS item_permanent_location_id,
    permanent_location_ref_data.locationname AS item_permanent_location_name,
    src_inventory_item.materialtypeid AS item_material_type_id,
    material_type_ref_data.materialtype AS item_material_type
   FROM src_inventory_item
     LEFT JOIN ( SELECT src_inventory_location.id,
            src_inventory_location.libraryid,
            src_inventory_location.jsonb ->> 'name' AS locationname
           FROM src_inventory_location) effective_location_ref_data ON effective_location_ref_data.id = src_inventory_item.effectivelocationid
     LEFT JOIN ( SELECT src_inventory_call_number_type.id,
            src_inventory_call_number_type.jsonb ->> 'name' AS item_effective_call_number_type_name
           FROM src_inventory_call_number_type) call_number_type_ref_data ON call_number_type_ref_data.id::text = ((src_inventory_item.jsonb -> 'effectiveCallNumberComponents') ->> 'typeId')
     LEFT JOIN ( SELECT src_inventory_call_number_type.id,
            src_inventory_call_number_type.jsonb ->> 'name' AS item_level_call_number_type_name
           FROM src_inventory_call_number_type) call_item_number_type_ref_data ON call_item_number_type_ref_data.id::text = (src_inventory_item.jsonb ->> 'itemLevelCallNumberTypeId')
     LEFT JOIN ( SELECT src_inventory_loclibrary.id,
            src_inventory_loclibrary.jsonb ->> 'name' AS item_effective_library_name,
            src_inventory_loclibrary.jsonb ->> 'code'::text AS item_effective_library_code
           FROM src_inventory_loclibrary) loclib_ref_data ON loclib_ref_data.id = effective_location_ref_data.libraryid
     LEFT JOIN ( SELECT src_inventory_location.id,
            src_inventory_location.jsonb ->> 'name' AS locationname
           FROM src_inventory_location) permanent_location_ref_data ON permanent_location_ref_data.id = src_inventory_item.permanentlocationid
     LEFT JOIN ( SELECT src_inventory_material_type.id,
            src_inventory_material_type.jsonb ->> 'name' AS materialtype
           FROM src_inventory_material_type) material_type_ref_data ON material_type_ref_data.id = src_inventory_item.materialtypeid;
