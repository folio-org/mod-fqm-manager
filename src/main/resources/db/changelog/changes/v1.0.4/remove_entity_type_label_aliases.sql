-- Remove label aliases from entity type definitions and their columns

UPDATE entity_type_definition src
  SET definition = (
    SELECT
      (src.definition::jsonb #- '{labelAlias}') -- remove root labelAlias
      || jsonb_build_object('columns', jsonb_agg(elem - 'labelAlias')) -- remove inner column labelAlias, concatenating back
    FROM entity_type_definition
    -- must unroll the array and iterate; no convenient operator :(
    -- https://stackoverflow.com/a/69482108/4236490
    CROSS JOIN lateral jsonb_array_elements(definition::jsonb#>'{columns}') as arr(elem) where src.id = id
  );
