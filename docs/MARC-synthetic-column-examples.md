# MARC synthetic column examples

This document shows example `EntityTypeColumn` shapes for the MARC scenarios we have been discussing for our POC.

The examples below are meant to represent the *runtime* synthetic columns created by the POC, not the generic placeholder defined in the JSON5 entity type file.

## Base assumption

These examples assume the generic placeholder column on `simple_srs_record` looks like this:

```js
{
  name: 'marc',
  labelAlias: 'MARC',
  dataType: {
    dataType: 'marcDataType',
  },
  queryable: true,
  hidden: true,
  visibleByDefault: false,
  essential: true,
  valueGetter: ':record_lb.matched_id',
}
```

At runtime, after source alias injection, that correlation expression becomes `"record_lb".matched_id`, which is what the synthetic columns below use.

All of the examples also assume the same common behavior:

- `dataType` is `marcDataType`
- `queryable` is `true`
- `visibleByDefault` is `false`
- `essential` is `false`
- `valueFunction` is `lower(:value)`

## 1. Tag only

Example dynamic field name:

- `marc_245`

Example synthetic column:

```js
{
  name: 'marc_245',
  labelAlias: '245',
  dataType: {
    dataType: 'marcDataType',
  },
  queryable: true,
  visibleByDefault: false,
  essential: false,
  valueGetter: `(
    SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
  )`,
  filterValueGetter: `(
    SELECT lower(string_agg(marc.value, ' ') FILTER (WHERE marc.value IS NOT NULL))
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
  )`,
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display all indexed values associated with tag `245`
- query those values as one searchable MARC field

## 2. Tag with indicator

Example dynamic field name:

- `marc_245_ind1`

Example synthetic column:

```js
{
  name: 'marc_245_ind1',
  labelAlias: '245 ind1',
  dataType: {
    dataType: 'marcDataType',
  },
  queryable: true,
  visibleByDefault: false,
  essential: false,
  valueGetter: `(
    SELECT jsonb_agg(DISTINCT marc.ind1) FILTER (WHERE marc.ind1 IS NOT NULL)
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
  )`,
  filterValueGetter: `(
    SELECT lower(string_agg(DISTINCT marc.ind1, ' ') FILTER (WHERE marc.ind1 IS NOT NULL))
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
  )`,
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display the first indicator values for tag `245`
- query those indicator values directly

## 3. Tag with subfield

Example dynamic field name:

- `marc_245_a`

Example synthetic column:

```js
{
  name: 'marc_245_a',
  labelAlias: '245$a',
  dataType: {
    dataType: 'marcDataType',
  },
  queryable: true,
  visibleByDefault: false,
  essential: false,
  valueGetter: `(
    SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
      AND marc.subfield_no = 'a'
  )`,
  filterValueGetter: `(
    SELECT lower(string_agg(marc.value, ' ') FILTER (WHERE marc.value IS NOT NULL))
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '245'
      AND marc.subfield_no = 'a'
  )`,
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display the indexed values for subfield `245$a`
- query only those subfield values

## 4. Tag with subfield and indicator

This case is **not implemented in the current first-pass grammar**, but if we decide to support it later, the synthetic column would likely look something like this.

Illustrative dynamic field name:

- `marc_650_a_ind2_7`

Illustrative synthetic column:

```js
{
  name: 'marc_650_a_ind2_7',
  labelAlias: '650$a (ind2=7)',
  dataType: {
    dataType: 'marcDataType',
  },
  queryable: true,
  visibleByDefault: false,
  essential: false,
  valueGetter: `(
    SELECT jsonb_agg(marc.value) FILTER (WHERE marc.value IS NOT NULL)
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '650'
      AND marc.subfield_no = 'a'
      AND marc.ind2 = '7'
  )`,
  filterValueGetter: `(
    SELECT lower(string_agg(marc.value, ' ') FILTER (WHERE marc.value IS NOT NULL))
    FROM ${tenant_id}_mod_source_record_storage.marc_indexers marc
    WHERE marc.marc_id = "record_lb".matched_id
      AND marc.field_no = '650'
      AND marc.subfield_no = 'a'
      AND marc.ind2 = '7'
  )`,
  valueFunction: 'lower(:value)',
}
```

Important note:

- this is just an example of the likely *shape*
- the exact future field-name grammar for this case is still undecided
- if we add this scenario, it should be treated as one combined synthetic field so the indicator and subfield constraints apply together

## Current POC status

Currently implemented by the POC:

- tag only
- tag with indicator
- tag with subfield

Not yet implemented by the current grammar:

- tag with subfield and indicator in one combined field
- leader position fields
- `006` / `007` / `008` byte-position fields
