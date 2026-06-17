# MARC Implementation Stories

This document captures an initial set of candidate implementation stories based on the MARC querying spike and the current POC.

These are not meant to be final Jira tickets yet. The goal is to give us a clean starting point for story breakdown, sequencing, and review with the team and SA.

## Recommended sequencing

Suggested order:

1. Backend hardening for the dynamic MARC querying MVP
2. Query builder / UI support for dynamic MARC selectors
3. Performance validation and operational guardrails
4. Blank indicator semantics
5. Leader and fixed-position MARC follow-up
6. Same-repeatable-entry correlation as a separate cross-cutting story

## Story 1: Backend hardening for dynamic MARC querying MVP

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
- Keep `marcDataType` behavior aligned with the current model:
  - aggregated display values via `valueGetter`
  - row-level `EXISTS` / `NOT EXISTS` query semantics
- Ensure referenced synthetic MARC fields can also be returned in results

### Why this story exists

This is the core recommendation from the spike. It proves we can avoid large predefined MARC column lists while still supporting useful MARC query combinations.

### Initial acceptance ideas

- A query referencing a valid dynamic MARC field name is accepted without that field being eagerly returned by `GET /entity-types/{id}`
- Tag-only, indicator-only, subfield-only, and constrained-subfield queries all produce the expected SQL behavior
- Referenced synthetic MARC fields can be included in query results
- Existing non-MARC query behavior is unaffected

### Out of scope

- Leader / `006` / `007` / `008`
- Same-repeatable-entry correlation across multiple predicates
- Blank-indicator policy beyond the currently supported non-blank case

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

### Why this story exists

The spike approach intentionally avoids giant field dropdowns. That means the UI needs a selector flow that can build dynamic MARC fields instead of relying on preloaded field metadata alone. It also means the UI has to take on most of the discovery/help responsibility for supported MARC combinations.

### Initial acceptance ideas

- A user can build a tag-only MARC query from the UI
- A user can build an indicator-only MARC query from the UI
- A user can build a subfield MARC query from the UI
- A user can build a constrained subfield query such as “245 ind1 = 7, subfield a contains X”
- The UI does not require all MARC combinations to appear in the normal entity-type field list
- Users can discover supported MARC query combinations through the UI without needing to know raw synthetic field names

### Open design questions

- Should the UI expose raw synthetic field names anywhere, or keep them fully internal?
- How should dynamic MARC fields be surfaced in visible-columns workflows?
- Do we want inline help/examples, a lightweight discovery panel, or both?

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

`marc_indexers` is normalized, which makes the approach feasible, but no indexes are available. The important question is not “can every MARC query be fast?” It is “can we support this safely without harming the rest of FOLIO?”

### Initial acceptance ideas

- We have representative measurements for the major supported MARC query shapes
- We can describe which query patterns appear safe, risky, or likely to need guardrails
- We can state whether the implementation is acceptable for initial rollout with the current database shape

### Important note

It is acceptable if some MARC queries are slow on very large datasets.

It is not acceptable if MARC querying materially degrades the performance or stability of other FOLIO apps.

## Story 4: Blank indicator semantics

### Goal

Define how blank indicators should be represented and queried.

### Scope

- Decide how blank indicator values are represented in the synthetic-field grammar, if supported
- Decide how blank should be treated in:
  - querying
  - display
  - empty/not-empty semantics
- Align backend and UI behavior on the same policy

### Why this story exists

Indicators are coded values, and blank is meaningful in MARC. We should not leave blank behavior undefined if indicator querying becomes a supported feature.

### Initial acceptance ideas

- The policy for blank indicators is documented
- The backend can apply the chosen blank-indicator rule consistently
- The UI can express the chosen blank-indicator case without ambiguity

### Example question to resolve

Should blank be represented as a special token in the field grammar, a special UI option, a normal string value, or some combination of those?

## Story 5: Leader and fixed-position MARC follow-up

### Goal

Extend the MARC query model to support leader positions and eventually `006` / `007` / `008` byte-position queries.

### Scope

- Decide whether leader positions use the same general dynamic-field approach
- Prototype naming and query semantics for leader positions
- Determine whether `006` / `007` / `008` should use a parallel grammar or a different model

### Why this story exists

The current POC and grammar focus on tag, indicator, and subfield combinations. Leader and fixed-position fields are a different category and should be treated as a follow-up instead of being forced into the first MVP.

### Initial acceptance ideas

- We have a documented naming/query proposal for leader positions
- We have decided whether leader support belongs in the next implementation pass
- We have a separate recommendation for `006` / `007` / `008`

## Story 6: Same-repeatable-entry correlation across multiple predicates

### Goal

Support queries where multiple conditions must all match within the same repeatable entry.

### Scope

- Handle same-entry semantics for repeatable structured data in general, not only MARC
- Ensure the future solution also covers MARC cases such as:
  - multiple subfield predicates that must match in the same logical MARC occurrence
  - guaranteed combined `ind1 + ind2` same-row semantics
- Determine whether the solution is generic, MARC-specific, or a hybrid

### Why this story exists

This is a broader FQM limitation that already exists outside MARC. The current constrained-subfield MARC support improves same-row precision for one fixed indicator plus one subfield, but it does not solve the more general same-repeatable-entry problem.

### Initial acceptance ideas

- We can express a same-entry constraint across multiple predicates in a supported way
- The solution applies to both MARC and non-MARC repeatable structured data
- The current MARC synthetic-field model can participate in that solution without requiring a full redesign

### Important note

The current `marcDataType` approach is compatible with this future direction. A likely implementation path would combine compatible MARC predicates into one row-level `EXISTS` rather than evaluating each one independently.

## Suggested MVP candidate set

If we want the smallest practical implementation set after the spike, I would start with:

- Story 1: Backend hardening for dynamic MARC querying MVP
- Story 2: Query builder / UI support for dynamic MARC selectors
- Story 3: Performance validation and operational guardrails
- Story 4: Blank indicator semantics

The other stories feel more like explicit follow-up work than first-pass MVP blockers.
