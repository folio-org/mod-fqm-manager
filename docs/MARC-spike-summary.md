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

Current parser constraints:

- uppercase input is accepted and normalized internally, so `MARC_245_A` behaves the same as `marc_245_a`
- control fields (`001`-`009`) are treated as tag-only fields, so indicator/subfield/constrained-subfield forms for `00X` tags are invalid
- subfield codes are currently modeled as single-character alphanumeric codes
- fixed indicator values in constrained-subfield fields are currently modeled as either a single alphanumeric character or the special token `blank`
- the backend currently maps the public blank-indicator token `blank` to the `marc_indexers` storage value `#`
- `blank` is distinct from `$empty`
  - `blank` means a matching MARC row exists and stores the blank indicator value
  - `$empty` means there is no matching usable row/value
- raw `#` should be treated as an internal storage detail, not the preferred public contract

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

There is also an important SRS-side data assumption behind the current entity model:

- `matched_id` is expected to identify the logical current SRS record that FQM should treat as one record
- the `mod-source-record-storage` team has confirmed that only one `ACTUAL` or `DELETED` record should exist for a given `matched_id`

This matters because `simple_srs_record` already uses `matched_id` as its entity ID column, and the MARC POC correlates `marc_indexers` rows through that same identifier.

Current interpretation:

- duplicate `ACTUAL` rows for the same `matched_id` should be treated as upstream data anomalies, not as a normal case the MARC querying design must support

One follow-up remains open:

- whether a `DRAFT` row can coexist with an `ACTUAL` or `DELETED` row for the same `matched_id`
- current expectation is that `DRAFT` should not be a special problem here and should likely follow the same general uniqueness model, but that still needs explicit confirmation

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
- indicator-only operator restrictions and final UI presentation for `blank` still need follow-up work
- leader is not part of the current grammar and has not yet been prototyped in this spike
- fixed-position fields (`006` / `007` / `008`) are also not part of the current grammar

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

### Preliminary performance results

Initial manual testing was run with:

- `mod-fqm-manager` running locally
- a test database with a large real dataset
- the `simple_srs_record` entity type
- no joins to Inventory instance data
- roughly 8 million searched records in scope

The run values below are recorded exactly as reported during testing.

- Constrained subfield equality
  - query: `{"marc_245_ind1_1_b": {"$eq": "permanent ed."}}`
  - matching records: `6,435`
  - runs: `13 / 6 / 6`

- Constrained subfield contains
  - query: `{"marc_650_ind2_7_a": {"$contains": "history"}}`
  - matching records: `17,854`
  - runs: `86 / 36 / 40`

- Tag contains
  - query: `{"marc_245": {"$contains": "the"}}`
  - matching records: `1,260,000`
  - runs: `188 / 199 / 177`

- Indicator-only equality
  - query: `{"marc_245_ind1": {"$eq": "1"}}`
  - matching records: `1,260,000`
  - runs: `216 / 217 / 219`

- Subfield equality
  - query: `{"marc_650_a": {"$eq": "history"}}`
  - matching records: `27,222`
  - runs: `15 / 13 / 17`

- Subfield contains
  - query: `{"marc_650_a": {"$contains": "history"}}`
  - matching records: `197,439`
  - runs: `42 / 40 / 43`

- Subfield contains, highly selective
  - query: `{"marc_100_a": {"$contains": "Shakespeare"}}`
  - matching records: `2,842`
  - runs: `4 / 4 / 5`

- Control-tag starts-with
  - query: `{"marc_001": {"$starts_with": "ins"}}`
  - matching records: `346,332`
  - runs: `51 / 55 / 58`

- Tag contains, broad repeatable field
  - query: `{"marc_650": {"$contains": "history"}}`
  - matching records: `797,549`
  - runs: `137 / 130 / 129`

- Indicator-only blank-value equality
  - query: `{"marc_035_ind1": {"$eq": "#"}}`
  - matching records: `1,260,000`
  - runs: `347 / 227 / 222`

Note:

- this test used the underlying storage value `#`
- the finalized public contract should use the token `blank` and map it internally to `#`

Initial observations from this small sample:

- selective subfield and constrained-subfield queries were the most favorable shapes in this dataset
- broad tag-level and indicator-only queries were noticeably heavier, especially when they matched very large portions of the data
- repeated runs often improved after the first run, which suggests some caching or warm-up effects
- the broadest indicator-only blank-value equality case was one of the heaviest tested shapes
- these results are encouraging as an initial signal, but they do not yet replace broader validation under concurrent load

One important caveat:

- these measurements cover query execution only
- they do not yet measure export-heavy or result-materialization-heavy cases where many synthetic MARC fields or full `content` are returned

### Export and result-materialization performance

Export and result retrieval should be treated as a separate performance concern from predicate evaluation.

Why this matters:

- MARC querying currently filters with row-level `EXISTS` logic
- but MARC values are still displayed and exported through aggregated `valueGetter` expressions
- that means a query can be acceptable at the filtering stage and still become expensive when many MARC fields are selected in the results

Important implications:

- returning only IDs is not the same cost as returning one or more synthetic MARC fields
- returning synthetic MARC fields is not the same cost as returning the full MARC `content` blob
- exporting large result sets with many dynamic MARC fields may be more expensive than the matching step itself
- repeated aggregated `valueGetter` evaluation across many rows and columns is a likely risk area for exports

What this means for testing:

- performance checks should include both query-match behavior and result-materialization behavior
- at least some tests should compare:
  - ID-only retrieval
  - ID plus synthetic MARC field retrieval
  - ID plus multiple synthetic MARC fields
  - ID plus full `content`

The practical concern is not just "can the query find the right records?" but also "can the system return or export the requested MARC data without unacceptable cost?"

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

At least some of those checks should also be repeated with different result shapes, for example:

- IDs only
- IDs plus one synthetic MARC field
- IDs plus several synthetic MARC fields
- IDs plus full `content`

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

One related design consequence:

- a single `marcDataType` is still sufficient for the backend model
- but operator choice may need to depend on the parsed MARC selector shape, not just the datatype
- indicator-only fields likely need coded-value operators
- tag, subfield, and constrained-subfield fields likely need text-search operators

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
- leader support in the first pass
- `006` / `007` / `008` fixed-position field support in the first pass
- final UI/operator alignment for blank-indicator handling

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
