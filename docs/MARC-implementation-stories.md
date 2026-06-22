# MARC Implementation Stories

This document captures an initial set of candidate implementation stories based on the MARC querying spike and the current POC.

These are not meant to be final Jira tickets yet. The goal is to give us a clean starting point for story breakdown, sequencing, and review with the team and SA.

## Recommended sequencing

Suggested order:

1. Add `marcType` support in `folio-query-tool-metadata`
2. Backend hardening for the dynamic MARC querying MVP
3. Query builder / UI support for dynamic MARC selectors
4. Performance validation and operational guardrails
5. Leader and fixed-position MARC follow-up
6. Same-repeatable-entry correlation as a separate cross-cutting story

## Story 0: Add `marcType` support in `folio-query-tool-metadata`

### Goal

Add `marcType` as a first-class shared DTO/data-type concept in `folio-query-tool-metadata`.

### Scope

- Add the new `marcType` definition to the shared metadata model
- Ensure downstream consumers can deserialize and use it cleanly
- Align the shared metadata contract with the behavior already proven in the POC
- Publish whatever shared-library version is needed for consuming modules

### Why this story exists

The current POC already depends on `marcType` conceptually. For implementation planning, this should be treated as an explicit cross-repo prerequisite rather than an implicit assumption.

### Initial acceptance ideas

- `marcType` exists in the shared metadata library
- `mod-fqm-manager` can consume the shared definition without local workarounds
- The shared contract is sufficient for the MARC-specific behavior described in this spike

### Important note

This story is a dependency/prerequisite for a clean production implementation even though the local POC was able to move ahead conceptually.

## Story 1: Backend hardening for the dynamic MARC querying MVP

### Goal

Turn the current POC into a supported backend implementation for dynamic MARC querying in FQM.

### Scope

- Keep one generic MARC capability on the entity type rather than predefining thousands of MARC columns
- Support dynamic field synthesis at query time for:
  - `marc_<tag>`
  - `marc_<tag>_ind1`
  - `marc_<tag>_ind2`
  - `marc_<tag>_<subfield>`
  - `marc_<tag>_ind1_<indicatorValue>_<subfield>`
  - `marc_<tag>_ind2_<indicatorValue>_<subfield>`
- Keep `marcType` behavior aligned with the current model:
  - aggregated display values via `valueGetter`
  - row-level `EXISTS` / `NOT EXISTS` query semantics
- Implement the finalized blank-indicator contract in the backend:
  - public token `blank`
  - internal/storage mapping to `#`
  - distinct from `$empty`
  - decide whether raw `#` is tolerated for direct API compatibility
- Add backend validation for MARC operator restrictions where selector shape requires it
  - especially indicator-only fields versus text-like MARC fields
- Ensure referenced synthetic MARC fields can also be returned in results

### Why this story exists

This is the core recommendation from the spike. It proves we can avoid large predefined MARC column lists while still supporting useful MARC query combinations.

### Initial acceptance ideas

- A query referencing a valid dynamic MARC field name is accepted without that field being eagerly returned by `GET /entity-types/{id}`
- Tag-only, indicator-only, subfield-only, and constrained-subfield queries all produce the expected SQL behavior
- Blank-indicator queries and constrained-subfield selectors behave consistently with the finalized `blank -> #` contract
- Invalid operator combinations for indicator-only MARC fields are rejected by the backend
- Referenced synthetic MARC fields can be included in query results
- Existing non-MARC query behavior is unaffected

### Out of scope

- Leader / `006` / `007` / `008`
- Same-repeatable-entry correlation across multiple predicates

### Optional fast-follow (small, separable)

A fifth MARC field shape that constrains **both** indicators in a single selector, compiling to one `EXISTS` for exact same-occurrence semantics:

- `marc_<tag>_ind1_<v1>_ind2` / `marc_<tag>_ind2_<v2>_ind1` — constrain one indicator, query the other (no subfield)
- `marc_<tag>_ind1_<v1>_ind2_<v2>_<subfield>` — constrain both indicators, query a subfield value

These follow the existing rule that **the last token is the single value-bearing target and everything before it is a baked constraint**. A form baking *both* indicator values with no subfield (`marc_<tag>_ind1_<v1>_ind2_<v2>`) is intentionally not used because it leaves nothing to query; instead one indicator is baked and the other becomes the queried target (e.g. `marc_245_ind1_1_ind2` with `{"$eq": "0"}`), which keeps a real runtime value and supports `eq`/`ne`/`in`/`nin`/`empty`.

This closes the only case where ANDing two single-indicator selectors is inexact (repeatable tags; it is already exact for non-repeatable tags like `245`). It is bounded — MARC 21 fixes the indicator count at exactly two — so it does not risk an exploding indicator grammar. It can ship with this story or as an independent follow-up.

## Story 2: Query builder / UI support for dynamic MARC selectors

### Goal

Allow users to construct MARC queries in the UI without having to know or type synthetic field names directly.

### Scope

- Provide MARC-specific query builder controls for:
  - tag
  - optional indicator number
  - optional indicator value
  - optional subfield
  - operator
  - query value
- Translate UI selections into the internal synthetic field format used by the backend
- Decide how users add MARC fields to visible columns or result display
- Decide how users discover supported MARC query combinations now that MARC fields are not eagerly listed in the entity type response
- Handle validation and guardrails for invalid combinations
- Use the finalized blank-indicator contract in the UI:
  - public token `blank`
  - no need for users to know the storage value `#`
  - keep `blank` distinct from `$empty`
- Choose operators based on the MARC selector shape, not only on `marcType`
  - indicator-only fields likely need coded-value operators like `eq`, `ne`, `in`, `nin`, and maybe `empty`
  - tag, subfield, and constrained-subfield fields likely need text-search operators like `contains`, `starts_with`, `eq`, and `ne`

### Why this story exists

The spike approach intentionally avoids giant field dropdowns. That means the UI needs a selector flow that can build dynamic MARC fields instead of relying on preloaded field metadata alone. It also means the UI has to take on most of the discovery/help responsibility for supported MARC combinations.

### Initial acceptance ideas

- A user can build a tag-only MARC query from the UI
- A user can build an indicator-only MARC query from the UI
- A user can build a subfield MARC query from the UI
- A user can build a constrained subfield query such as “245 ind1 = 7, subfield a contains X”
- A user can build a blank-indicator query or constrained-subfield selector using `blank` without needing to know `#`
- The UI does not require all MARC combinations to appear in the normal entity-type field list
- Users can discover supported MARC query combinations through the UI without needing to know raw synthetic field names

### Open design questions

- Should the UI expose raw synthetic field names anywhere, or keep them fully internal?
- How should dynamic MARC fields be surfaced in visible-columns workflows?
- Do we want inline help/examples, a lightweight discovery panel, or both?

Current note:

- the backend does not currently enforce MARC operator restrictions by field shape
- the recommended direction is for the UI to hide invalid operators for usability and for the backend to enforce the same restrictions for direct API callers

## Story 3: Performance validation and operational guardrails

### Goal

Validate the dynamic MARC approach against realistic data and define guardrails if broad MARC searches are expensive.

### Scope

- Test representative query shapes against realistic volumes:
  - tag-only
  - indicator-only
  - subfield-only
  - constrained subfield
  - broad `contains`
  - multiple MARC predicates in one query
- Measure whether MARC query load has unacceptable impact beyond this entity type
- Identify whether any guardrails are needed, such as:
  - limiting especially broad query patterns
  - narrowing the initial operator set

### Why this story exists

`marc_indexers` is normalized, which makes the approach feasible, but we should not assume extra indexes will be available. The important question is not “can every MARC query be fast?” It is “can we support this safely without harming the rest of FOLIO?”

We also already have an initial baseline from preliminary manual testing against a large test dataset. That gives this story a starting point, but not a final conclusion, especially for export/result-materialization behavior and broader concurrent-load validation.

### Initial acceptance ideas

- We have representative measurements for the major supported MARC query shapes
- We can describe which query patterns appear safe, risky, or likely to need guardrails
- We can state whether the implementation is acceptable for initial rollout with the current database shape
- We can state whether the implementation still needs more detailed validation before production release

### Important note

It is acceptable if some MARC queries are slow on very large datasets.

It is not acceptable if MARC querying materially degrades the performance or stability of other FOLIO apps.

## Story 4: Leader and fixed-position MARC follow-up

### Goal

Extend the MARC query model to support leader positions and eventually `006` / `007` / `008` byte-position queries.

### Scope

- Decide whether leader positions use the same general dynamic-field approach
- Prototype naming and query semantics for leader positions
- Determine whether `006` / `007` / `008` should use a parallel grammar or a different model

### Why this story exists

The current POC and grammar focus on tag, indicator, and subfield combinations. Leader and fixed-position fields are a different category and should be treated as a follow-up instead of being forced into the first MVP.

Current note:

- leader has not yet been prototyped in this spike
- it should be treated as a separate design follow-up, even if it ends up being simpler than `006` / `007` / `008`

### Initial acceptance ideas

- We have a documented naming/query proposal for leader positions
- We have decided whether leader support belongs in the next implementation pass
- We have a separate recommendation for `006` / `007` / `008`

## Story 5: Same-repeatable-entry correlation across multiple predicates

### Goal

Support queries where multiple conditions must all match within the same repeatable entry.

### Scope

- Handle same-entry semantics for repeatable structured data in general, not only MARC
- Ensure the future solution also covers MARC cases such as:
  - multiple subfield predicates that must match in the same logical MARC occurrence
- Note that combined `ind1 + ind2` same-row matching is **not** part of this story: both indicators are stored on the same `marc_indexers` row, so it is a single-row constraint solvable by a small grammar extension rather than by occurrence correlation
- Determine whether the solution is generic, MARC-specific, or a hybrid

### Why this story exists

This is a broader FQM limitation that already exists outside MARC. The current constrained-subfield MARC support improves same-row precision for one fixed indicator plus one subfield, but it does not solve the more general same-repeatable-entry problem.

### Important data constraint

`marc_indexers` does not carry a per-occurrence discriminator. Two occurrences of the same tag/subfield are stored as rows that differ only by `value` (the spike confirmed this with two `035 $a` rows whose trailing column was identical). This means same-occurrence correlation **cannot be solved against `marc_indexers` alone** — a solution would need to correlate against the MARC `content` JSONB blob or a schema that adds an occurrence key.

Note also that combined `ind1 + ind2` constraints are **not** part of this story: because both indicators live on the same `marc_indexers` row, that case is a single-row constraint and only needs a small grammar extension, independent of this same-occurrence work.

### Initial acceptance ideas

- We can express a same-entry constraint across multiple predicates in a supported way
- The solution applies to both MARC and non-MARC repeatable structured data
- The current MARC synthetic-field model can participate in that solution without requiring a full redesign

### Important note

The current `marcType` approach is compatible with this future direction. A likely implementation path would combine compatible MARC predicates into one row-level `EXISTS` rather than evaluating each one independently.

## Suggested MVP candidate set

If we want the smallest practical implementation set after the spike, I would start with:

- Story 0: Add `marcType` support in `folio-query-tool-metadata`
- Story 1: Backend hardening for dynamic MARC querying MVP
- Story 2: Query builder / UI support for dynamic MARC selectors
- Story 3: Performance validation and operational guardrails

The other stories feel more like explicit follow-up work than first-pass MVP blockers.
