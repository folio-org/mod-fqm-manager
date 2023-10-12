CREATE OR REPLACE VIEW drv_item_holdingsrecord_instance
 AS
 SELECT src_inventory_item.id,
    (instance_details.jsonb -> 'metadata'::text) ->> 'createdDate'::text AS instance_created_date,
    hrim.instanceid AS instance_id,
    jsonb_path_query_first(instance_details.jsonb, '$."contributors"[*]?(@."primary" == true)."name"'::jsonpath) #>> '{}'::text[] AS instance_primary_contributor,
    instance_details.jsonb ->> 'title'::text AS instance_title,
    (instance_details.jsonb -> 'metadata'::text) ->> 'updatedDate'::text AS instance_updated_date,
    src_inventory_item.jsonb ->> 'barcode'::text AS item_barcode,
    src_inventory_item.jsonb ->> 'copyNumber'::text AS item_copy_number,
    (src_inventory_item.jsonb -> 'metadata'::text) ->> 'createdDate'::text AS item_created_date,
    (src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'prefix'::text AS item_effective_call_number_prefix,
    (src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text AS item_effective_call_number_callNumber,
    (src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text AS item_effective_call_number_suffix,
    src_inventory_item.jsonb ->> 'copyNumber'::text AS item_effective_call_number_copyNumber,
    concat_ws(', ',
    		NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'prefix'::text, ''),
    		NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text, ''),
    		NULLIF((src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text, ''),
    		NULLIF(src_inventory_item.jsonb ->> 'copyNumber'::text, '')
    ) AS item_effective_call_number,
    (src_inventory_item.jsonb -> 'effectiveCallNumberComponents'::text) ->> 'typeId'::text AS item_effective_call_number_typeid,
    src_inventory_item.effectivelocationid AS item_effective_location_id,
    src_inventory_item.jsonb ->> 'hrid'::text AS item_hrid,
    src_inventory_item.holdingsrecordid AS holdings_id,
    src_inventory_item.jsonb ->> 'itemLevelCallNumber'::text AS item_level_call_number,
    src_inventory_item.jsonb ->> 'itemLevelCallNumberTypeId'::text AS item_level_call_number_typeid,
    src_inventory_item.materialtypeid AS item_material_type_id,
    src_inventory_item.permanentlocationid AS item_permanent_location_id,
    src_inventory_item.temporarylocationid AS item_temporary_location_id,
    "left"(lower(f_unaccent((src_inventory_item.jsonb -> 'status'::text) ->> 'name'::text)), 600) AS item_status,
    (src_inventory_item.jsonb -> 'metadata'::text) ->> 'updatedDate'::text AS item_updated_date
   FROM src_inventory_item
     JOIN src_inventory_holdings_record hrim ON src_inventory_item.holdingsrecordid = hrim.id
     JOIN src_inventory_instance instance_details ON hrim.instanceid = instance_details.id;
