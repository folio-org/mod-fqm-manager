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

For the query SQL examples below, assume the tenant has already been expanded to `diku`, so the runtime table name is `diku_mod_source_record_storage.marc_indexers`.

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

## 4. Why tag + subfield + indicator is not part of the current POC

This case is intentionally **not** part of the current first-pass grammar.

The reason is not that the SQL would be impossible. The problem is that the field contract becomes messy.

A normal subfield field has one natural query value:

- `marc_245_a contains 'Shakespeare'`

The queried value is the subfield value.

A hypothetical tag + subfield + indicator field would need two different pieces of query data:

- the subfield value, for example `contains 'Nuclear energy'`
- the indicator constraint, for example `ind2 = '7'`

Under the current dynamic MARC model, each predicate is still basically:

- field name
- operator
- value

That means only one of those two things can be the actual query value. If we try to solve that by putting the indicator value into the field name, the field name starts carrying part of the predicate.

Example of the kind of shape we do **not** want to rely on:

- `marc_650_a_ind2_7 contains 'Nuclear energy'`

Why that is a poor contract:

- the field name is no longer just identifying the target value
- part of the filter logic is hidden inside the field name
- the query value only represents the subfield content, not the indicator constraint
- it does not scale well if we later need richer same-occurrence logic

Current conclusion:

- `marc_<tag>`, `marc_<tag>_ind1`, `marc_<tag>_ind2`, and `marc_<tag>_<subfield>` are good fits for the current model
- subfield+indicator combinations are not
- if we ever need that capability later, it should probably use MARC-specific predicate metadata or a MARC-specific query structure rather than a longer field name

## Current POC status

Currently implemented by the POC:

- tag only
- tag with indicator
- tag with subfield

Not yet implemented by the current grammar:

- tag with subfield and indicator in one combined field
- leader position fields
- `006` / `007` / `008` byte-position fields
