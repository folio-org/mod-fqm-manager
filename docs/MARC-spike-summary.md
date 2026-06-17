# MARC Spike Summary

This document summarizes the outcome of the MARC querying spike and is intended to serve as the primary review artifact for the team and SA.

It pulls together:

- the recommended approach
- what the current proof of concept demonstrates
- the major pros and cons
- feasibility and likely implementation requirements
- performance and scale considerations
- index implications
- query builder / UI implications
- suggested implementation stories

## Recommendation

Recommend a single generic MARC capability on the entity type, with dynamic MARC field resolution at query time, rather than predefining a large static set of MARC columns.

Instead of eagerly exposing fields like:

- `marc_245_a`
- `marc_100_1_a`
- `marc_700_t`

the backend keeps one generic MARC capability and interprets concrete MARC field names dynamically when they are actually referenced in a query or selected for results.

This avoids creating an unwieldy set of fields in query-builder dropdowns and visible-column lists while still allowing meaningful MARC query combinations.

## Proof of concept outcome

The current POC demonstrates that the recommended approach is technically feasible in FQM.

### What the POC proves

- A single generic MARC placeholder/capability can be declared on the entity type
- Concrete MARC field names can be interpreted dynamically at query time
- Those synthetic MARC fields can be queried against `marc_indexers`
- Those synthetic MARC fields can also be returned in query results
- The approach works with the current `marcDataType` model and current FQM query flow

### Dynamic MARC query shapes currently supported by the POC

- tag only
  - example: `marc_245`
- indicator only
  - example: `marc_245_ind1`
- subfield only
  - example: `marc_245_a`
- constrained subfield with fixed indicator value
  - example: `marc_245_ind1_7_a`

### Current supported grammar

- `marc_<tag>`
- `marc_<tag>_ind1`
- `marc_<tag>_ind2`
- `marc_<tag>_<subfield>`
- `marc_<tag>_ind1_<indicatorValue>_<subfield>`
- `marc_<tag>_ind2_<indicatorValue>_<subfield>`

## Feasibility and implementation requirements

### Feasibility

The approach is feasible.

The main reason is that `marc_indexers` is already normalized into useful queryable parts, including:

- `marc_id`
- `field_no`
- `ind1`
- `ind2`
- `subfield_no`
- `value`

That structure is sufficient to support dynamic MARC selectors with row-level `EXISTS` logic even without eagerly predefining columns.

### What implementation requires

- formalize `marcDataType` in `folio-query-tool-metadata` as a shared prerequisite
- keep `marcDataType` as the MARC-specific field type
- keep one generic MARC capability on the entity type
- synthesize concrete MARC fields dynamically from field names
- continue to use aggregated `valueGetter` logic for display
- continue to use MARC-specific row-level `EXISTS` / `NOT EXISTS` semantics for querying
- ensure synthetic MARC fields can be included in results when referenced by the query
- provide a query-builder / UI flow that constructs MARC selectors dynamically instead of relying on giant preloaded field lists

## Pros

- avoids a massive predefined MARC field list in the entity type definition
- avoids huge dropdowns in query-building and visible-columns flows
- keeps the backend flexible and allows new valid MARC combinations without enumerating every field up front
- fits the normalized `marc_indexers` data model well
- works with the current FQM query path and `marcDataType`
- supports more precise indicator+subfield querying than a fully separated indicator-only / subfield-only approach
- keeps the synthetic field names mostly as an internal/backend contract that the UI can generate for the user

## Cons and limitations

- the backend contract becomes more specialized and MARC-specific
- the query builder / UI must do more work because normal entity-type field discovery is no longer enough
- broad MARC `contains` queries may be expensive, especially at scale
- the current implementation does not solve the broader same-repeatable-entry correlation problem
- guaranteed combined `ind1 + ind2` same-row semantics are not solved by the current implementation
- blank indicator semantics still need an explicit design decision
- leader and fixed-position fields (`006` / `007` / `008`) are not part of the current grammar

## Performance and scale

### Current position

The spike shows that the approach is logically feasible, but it does not yet prove production-scale performance.

The right performance framing is:

- it is acceptable if some MARC queries on this entity type are slow on very large datasets
- it is not acceptable if MARC querying materially degrades the performance or stability of other FOLIO apps

### Important context

- `marc_indexers` is normalized, which is a positive
- we should not assume additional indexes will be available
- because of that, the main question is not “can every MARC query be made fast?”
- the main question is “can we support this query model without causing unacceptable platform impact?”

### Preliminary performance assessment

Based on the current SQL shape, the likely performance profile is:

- indicator equality queries are likely to be among the narrower/safer cases
- subfield equality or constrained-subfield equality should usually be narrower than tag-wide text searching
- broad `contains` searches on tag-level or subfield-level values are likely to be the riskiest query shapes
- cost will likely scale with the number of MARC predicates in one query, because each predicate currently becomes a separate correlated `EXISTS`

### Preliminary performance checks to run on a test DB

Suggested representative checks:

1. Tag-only broad text search
   - `{"marc_245": {"$contains": "shakespeare"}}`

2. Subfield-only broad text search
   - `{"marc_245_a": {"$contains": "shakespeare"}}`

3. Indicator equality search
   - `{"marc_245_ind1": {"$eq": "1"}}`

4. Constrained subfield search
   - `{"marc_245_ind1_7_a": {"$contains": "shakespeare"}}`

5. Multiple MARC predicates in one query
   - `{"$and": [ ... ]}` equivalent, for example:
   - `marc_245_a contains X AND marc_650_a contains Y`

6. Approximate dual-indicator case
   - `marc_245_ind1_7_a = 'xyz' AND marc_245_ind2_7_a = 'xyz'`

For each, capture at least:

- total execution time
- execution plan shape if available
- whether the query appears to scan broadly
- whether concurrent load causes unacceptable impact beyond this entity type

## Index impact

The approach does not depend on new indexes to be logically correct.

However:

- absence of indexes increases the importance of query-shape validation
- if indexes are unavailable, operator choice and guardrails become more important
- broad text-search patterns should be treated as the highest-risk shapes until tested

So the recommendation is:

- do not treat lack of indexes as a blocker
- do treat it as a scale/performance risk that should be validated honestly

## Query builder / UI impact

This approach has a meaningful UI impact.

Because MARC fields are not eagerly listed in `GET /entity-types/{id}`, the query builder cannot rely on a giant predefined MARC field list.

Instead, the UI should provide a MARC-specific selector flow for:

- tag
- optional indicator number
- optional indicator value
- optional subfield
- operator
- query value

The UI should ideally let users express MARC intent directly, while keeping raw synthetic field names internal.

Examples of what the UI should be able to build:

- tag-only query
- indicator-only query
- subfield-only query
- constrained subfield query like:
  - tag `245`
  - `ind1 = 7`
  - subfield `a`
  - value contains `Shakespeare`

The UI also needs to decide how dynamic MARC fields are surfaced in visible-columns workflows and in general MARC-query discoverability/help.

## What remains out of scope for the current approach

The current recommendation does **not** solve:

- multiple subfield predicates that must all match within the same repeatable MARC occurrence
- guaranteed same-row combined `ind1 + ind2` semantics
- exact reconstructed full-field string matching
- leader and fixed-position field support in the first pass
- final blank-indicator semantics

The multi-indicator case is best treated as part of the broader “multiple conditions must match within the same repeatable entry” story, not as a separate MARC-only problem.

## Suggested implementation stories

Recommended stories from the spike:

1. Add `marcDataType` support in `folio-query-tool-metadata`
2. Backend hardening for dynamic MARC querying MVP
3. Query builder / UI support for dynamic MARC selectors
4. Performance validation and operational guardrails
5. Blank indicator semantics
6. Leader and fixed-position MARC follow-up
7. Same-repeatable-entry correlation across multiple predicates

See [MARC-implementation-stories.md](/Users/bsharp/workspace/mod-fqm-manager/docs/MARC-implementation-stories.md) for the draft story breakdown.

## Bottom line

The spike supports recommending the dynamic MARC approach.

The best current recommendation is:

- use one generic MARC capability
- resolve MARC fields dynamically at query time
- support tag, indicator, subfield, and constrained-subfield shapes
- keep the current limitations explicit
- validate performance on a real test DB with special attention to broad text searches and overall platform impact

This gives FQM a flexible MARC query model without exploding the entity-type field list, while still leaving the broader repeatable-entry correlation problem as a separate follow-up concern.
