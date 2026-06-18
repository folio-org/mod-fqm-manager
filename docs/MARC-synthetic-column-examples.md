# MARC synthetic column examples

This document shows example `EntityTypeColumn` shapes for the MARC scenarios we have been discussing.

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

For the query SQL examples below, assume the tenant has already been expanded to `diku`, so the runtime table name is `diku_mod_source_record_storage.marc_indexers`.

Current parser constraints:

- uppercase field-name input is accepted and normalized internally, so `MARC_245_A` resolves the same way as `marc_245_a`
- control fields (`001`-`009`) are tag-only in the current grammar, so `marc_001_ind1`, `marc_001_a`, and similar shapes are invalid
- subfield codes are currently limited to single-character alphanumeric codes
- fixed indicator values in constrained-subfield fields are currently limited to either a single alphanumeric character or the special token `blank`
- the backend maps the public blank-indicator token `blank` to the current `marc_indexers` storage value `#`

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
  filterValueGetter: 'lower(marc.value)',
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display all indexed values associated with tag `245`
- query those values as one searchable MARC field

Query SQL example:

- Dynamic field name: `marc_245`
- Representative FQL: `{"marc_245": {"$contains": "Shakespeare"}}`
- Effective `filterValueGetter`:

```sql
lower(marc.value)
```

- Effective SQL predicate shape:

```sql
exists (
  select 1
  from diku_mod_source_record_storage.marc_indexers marc
  where marc.marc_id = "record_lb".matched_id
    and marc.field_no = '245'
    and lower(marc.value) like '%' || lower(:value) || '%'
)
```

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
  filterValueGetter: 'lower(marc.ind1)',
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display the distinct indicator values for tag `245`
- query those indicator values by checking whether a matching indicator value is present

Query SQL example:

- Dynamic field name: `marc_245_ind1`
- Representative FQL: `{"marc_245_ind1": {"$eq": "1"}}`
- Effective `filterValueGetter`:

```sql
lower(marc.ind1)
```

- Effective SQL predicate shape:

```sql
exists (
  select 1
  from diku_mod_source_record_storage.marc_indexers marc
  where marc.marc_id = "record_lb".matched_id
    and marc.field_no = '245'
    and lower(marc.ind1) = lower(:value)
)
```

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
  filterValueGetter: 'lower(marc.value)',
  valueFunction: 'lower(:value)',
}
```

Meaning:

- display the indexed values for subfield `245$a`
- query only those subfield values

Query SQL example:

- Dynamic field name: `marc_245_a`
- Representative FQL: `{"marc_245_a": {"$contains": "Shakespeare"}}`
- Effective `filterValueGetter`:

```sql
lower(marc.value)
```

- Effective SQL predicate shape:

```sql
exists (
  select 1
  from diku_mod_source_record_storage.marc_indexers marc
  where marc.marc_id = "record_lb".matched_id
    and marc.field_no = '245'
    and marc.subfield_no = 'a'
    and lower(marc.value) like '%' || lower(:value) || '%'
)
```

## 4. Constrained subfield with fixed indicator value

This case is now supported by the current MARC grammar.

Example dynamic field name:

- `marc_245_ind1_7_a`

Meaning:

- tag `245`
- only rows where `ind1 = '7'`
- query and display the value of subfield `a`

The important modeling choice is that this is still a **subfield value query**. The indicator value is not the runtime query value. It is a fixed selector built into the field name.

Example synthetic column:

```js
{
  name: 'marc_245_ind1_7_a',
  labelAlias: '245 ind1=7 $a',
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
      AND lower(marc.ind1) = '7'
      AND marc.subfield_no = 'a'
  )`,
  filterValueGetter: 'lower(marc.value)',
  valueFunction: 'lower(:value)',
}
```

Query SQL example:

- Dynamic field name: `marc_245_ind1_7_a`
- Representative FQL: `{"marc_245_ind1_7_a": {"$contains": "Shakespeare"}}`
- Effective `filterValueGetter`:

```sql
lower(marc.value)
```

- Effective SQL predicate shape:

```sql
exists (
  select 1
  from diku_mod_source_record_storage.marc_indexers marc
  where marc.marc_id = "record_lb".matched_id
    and marc.field_no = '245'
    and lower(marc.ind1) = '7'
    and marc.subfield_no = 'a'
    and lower(marc.value) like '%' || lower(:value) || '%'
)
```

Why this is a cleaner direction than earlier ideas:

- the queried value is still only the subfield value
- the indicator is treated as a fixed row constraint
- the last token still identifies the value-bearing target

Important boundary:

- this solves the "subfield value with a fixed indicator constraint" case
- it would **not** solve the broader "multiple queried values must all match within the same repeatable MARC occurrence" problem

Related nuance:

- a query like `marc_245_ind1_7_a = 'xyz' AND marc_245_ind2_7_a = 'xyz'` is a useful approximation for "both indicators are 7 for the same subfield value"
- but it is not a guaranteed same-row `ind1 + ind2` match, because each constrained field is still evaluated independently
- this should be considered part of the broader same-repeatable-entry problem, so a future same-entry implementation should also cover this MARC case
- with the current `marcDataType` approach, that future implementation would likely do this by combining compatible MARC predicates into one row-level `EXISTS`

Other remaining boundaries:

- multiple subfield predicates still cannot be guaranteed to match within the same repeatable MARC occurrence
- blank indicator handling now uses the public token `blank`, which is mapped internally to `#`

## Current POC status

Currently implemented by the POC:

- tag only
- tag with indicator
- tag with subfield
- constrained subfield with fixed indicator value

Not yet implemented by the current grammar:

- leader position fields
- `006` / `007` / `008` byte-position fields
