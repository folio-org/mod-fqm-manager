# MARC POC Working Notes

This document is a working place to gather the current assumptions, scope decisions, constraints, and open questions for the MARC POC.

The intent is to keep the implementation discussion in one place instead of spreading it across spike notes and chat history.

## Goal

Build a proof of concept for supporting dynamic MARC field references from one generic MARC capability on an entity type.

The system should be able to interpret concrete MARC field names at query time for:

- tag only
- tag + indicator
- tag + subfield
- tag + subfield + indicator, if and only if that can be expressed without introducing same-occurrence correlation semantics beyond current FQM behavior

Examples:

- `marc_001`
- `marc_245`
- `marc_245_ind1`
- `marc_245_a`
- `marc_650_a`
- `marc_700`

## POC Scope

The POC should prove the following:

- A single generic MARC placeholder/capability can be declared in an entity type definition.
- A query can reference concrete MARC field names dynamically, without those fields being eagerly listed in the entity type definition.
- Dynamically resolved MARC fields can query MARC data from `marc_indexers`. (Leader data in `marc_indexers_leader` is out of scope for this POC and is not yet queried.)
- Dynamically resolved MARC fields can display MARC values in query results.
- Dynamic MARC fields work with existing FQM query behavior and existing composite-entity query composition.

## Current Working Assumptions

### 1. `marcDataType` exists

`marcDataType` has already been added and is available for use in this project.

The current working expectation is:

- MARC fields display like multi-valued MARC data.
- MARC fields query via MARC-specific row-level `EXISTS` / `NOT EXISTS` semantics.
- MARC fields may expose a more restricted operator set than normal array-like fields.

Important note about operators:

- a single `marcDataType` is still the preferred model
- however, not every MARC field shape should necessarily expose the same operators
- indicator-only MARC fields behave more like coded-value fields
- tag, subfield, and constrained-subfield MARC fields behave more like text-search fields
- this likely means operator choice should depend on the parsed MARC selector shape, not only on `marcDataType`
- the current backend does not yet enforce those distinctions, so indicator-only MARC fields can still use text-style operators unless we add explicit validation
- recommended direction: keep one `marcDataType`, mirror operator restrictions in the UI for usability, and enforce them in the backend for correctness

### 2. Expansion should follow the existing read-time pattern

The current repository already expands `customFieldType` columns at read time in:

- [`EntityTypeRepository.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/repository/EntityTypeRepository.java)

Current note:

- This remains a useful reference pattern, but the current preferred MARC direction is no longer eager expansion of thousands of concrete fields.
- The POC is now leaning toward dynamic field resolution at query time instead.

### 3. Initial target entity type should be SRS

The most natural initial target for the POC is:

- [`simple_srs_record.json5`](/Users/bsharp/workspace/mod-fqm-manager/src/main/resources/entity-types/srs/simple_srs_record.json5)

Reason:

- It already has access to SRS MARC record data.
- It is the clearest place to prove tag / indicator / subfield querying against MARC records.

### 4. Column naming should stay simple and deterministic

Current preferred naming contract:

- `marc_<tag>`
- `marc_<tag>_ind1`
- `marc_<tag>_ind2`
- `marc_<tag>_<subfield>`

Examples:

- `marc_001`
- `marc_245`
- `marc_245_ind1`
- `marc_245_ind2`
- `marc_245_a`
- `marc_650_a`
- `marc_700`

Current assumption:

- We should derive dynamic MARC field behavior from the requested field name instead of introducing a complicated selector language for the POC.
- The parser should accept uppercase input and normalize MARC selector parts internally, so `MARC_245_A` behaves the same as `marc_245_a`.
- The current grammar treats subfield codes as single-character alphanumeric codes.
- Control fields (`001`-`009`) should be treated as tag-only fields in the current grammar. Indicator, subfield, and constrained-subfield variants for `00X` tags should be rejected.

### 5. Indicators are not a special contract category

Indicators should be treated similarly to tags and subfields at the contract level.

That means:

- `marc_245_ind1` simply means "query/display indicator 1 for tag 245"
- `marc_245_ind2` simply means "query/display indicator 2 for tag 245"

Current assumption:

- Indicator support does not require special condition syntax in the generated column name for this POC.
- Indicator positions are limited to `ind1` and `ind2`, which is the complete MARC indicator model we need to support. This is fixed by the MARC 21 standard (every variable data field has exactly two indicators; control `00X` fields have none), so there is no risk of needing `ind3`/`ind4` and the indicator grammar is permanently bounded.
- Fixed indicator values in constrained-subfield fields are currently modeled as single-character alphanumeric values.
- Uppercase indicator input should be accepted and normalized internally, so `MARC_245_IND1` behaves the same as `marc_245_ind1`.
- Blank indicators can be represented with the public token `blank`, which the backend maps to the current `marc_indexers` storage value `#`.
- For indicator-only queries, a runtime value like `{"marc_245_ind1": {"$eq": "blank"}}` should be supported.
- Blank should remain distinct from `$empty`.
  - `blank` means a matching MARC row exists and stores the blank indicator value.
  - `$empty` means there is no matching usable row/value.
- Raw `#` should be treated as an internal storage detail, not the preferred public contract.

### 6. Tag + subfield + indicator can be modeled as a constrained subfield extension

We have discussed a possible desire to support a shape like:

- tag + subfield + indicator

The important distinction is whether this means:

- "query/display the indicator itself" or
- "query/display a subfield value, but only when the same MARC row or occurrence has a matching indicator value"

The first case is already covered by normal indicator columns like:

- `marc_245_ind1`
- `marc_245_ind2`

The second case is the more interesting one. We now support it by treating it as a constrained subfield field.

Example:

- `marc_245_ind1_7_a contains 'abc'`

In that model:

- `245` selects the tag
- `ind1 = '7'` is a fixed row constraint
- `a` is the value-bearing subfield
- the user-provided query value is still applied only to `marc.value`

That gives the field one natural query value again: the subfield value. The indicator value is not competing with the query value at runtime; it is baked into the field definition as part of the selector.

Supported grammar for this extension:

- `marc_<tag>_ind1_<indicatorValue>_<subfield>`
- `marc_<tag>_ind2_<indicatorValue>_<subfield>`

Examples:

- `marc_245_ind1_7_a`
- `marc_245_ind1_blank_a`
- `marc_650_ind2_7_a`

Current assumption:

- The constrained indicator comes before the subfield.
- The last token still identifies the value-bearing target.
- We do not need the reverse shape, because the point of the field is still to query the subfield value while applying a fixed indicator constraint.
- This should be treated as a targeted extension to the MARC grammar, not as a general solution for repeatable-field correlation.
- The current implementation expects the baked-in indicator value to be either a single alphanumeric character or the special token `blank`.
- The current implementation expects the subfield code to be a single alphanumeric character.

Approximation we can already make:

- `marc_245_ind1_7_a = 'xyz' AND marc_245_ind2_7_a = 'xyz'`

This will often behave the way a user expects, especially for nonrepeatable tags or when the subfield value is highly specific. In many real records, it is more likely that both conditions refer to the same MARC row than that two different rows happen to share the same subfield value under different indicators.

However, it is still only an approximation. The query means:

- there exists a row where `ind1 = '7'`, `subfield = 'a'`, and `value = 'xyz'`
- and there exists a row where `ind2 = '7'`, `subfield = 'a'`, and `value = 'xyz'`

Those two matches could still come from different rows. So this is useful in practice, but it should not be documented as guaranteeing a combined `ind1 + ind2` same-row match.

Important clarification on the `ind1 + ind2` case specifically:

- this approximation only needs two separate constrained fields because the grammar has no single form for constraining both indicators at once
- but `marc_indexers` stores both `ind1` and `ind2` on the **same** row, so "the same field occurrence has `ind1 = 7` and `ind2 = 0` and `subfield a = X`" is a single-row constraint that one `EXISTS` could express exactly
- the `AND` workaround is in fact **exact for non-repeatable tags** (e.g. `245`, `100`, `130`), because the record has at most one such field, so both indicator conditions must refer to the same occurrence; it is only **approximate for repeatable tags** (e.g. `650`, `700`), where the two `EXISTS` clauses could match different occurrences
- therefore combined `ind1 + ind2` is a **grammar gap**, not a same-row correlation problem, and is separable from the broader same-occurrence work below
- the genuinely hard case is multiple **subfield** predicates within the same repeatable occurrence (see "Explicitly Out of Scope" below), which `marc_indexers` cannot express at all

Proposed fast-follow grammar to close the gap exactly (a fifth MARC field shape):

- `marc_<tag>_ind1_<v1>_ind2_<v2>` — both indicators constrained, no subfield
- `marc_<tag>_ind1_<v1>_ind2_<v2>_<subfield>` — both indicators plus a subfield value

Both compile to a single `EXISTS` and give exact same-occurrence semantics. MARC 21 fixes the indicator count at exactly two (`ind1`, `ind2`; control `00X` fields have none), so this is the natural ceiling and the indicator grammar cannot grow beyond it.

This also overlaps with the broader repeatable-field correlation story. Even if we solved the "subfield value plus indicator value on the same row" case, that still would not solve the more general "multiple queried values must all match within the same repeatable occurrence" problem across multiple MARC rows.

How this relates to the broader same-entry story:

- the combined `ind1 + ind2` case should be treated as part of that broader repeatable-entry problem
- a future implementation of same-entry correlation should also be expected to handle this MARC case
- the current `marcDataType` / `EXISTS` approach is compatible with that direction
- the future solution would likely need to combine compatible MARC predicates into one row-level `EXISTS` rather than evaluating them as separate independent `EXISTS` clauses

Remaining boundaries of this approach:

- multiple subfield predicates that must all match within the same repeatable MARC occurrence are still not solved
- guaranteed combined `ind1 + ind2` same-row constraints are still not solved by the current implementation
- query-match cost and export/result-materialization cost should be treated separately, because MARC filtering uses row-level `EXISTS` but MARC display/export still uses aggregated `valueGetter` expressions

### 7. `GET /entity-types/{id}` should not eagerly return all supported MARC fields

Current preferred direction:

- The entity type should expose a generic MARC capability, not a pre-expanded list of every supported MARC field.
- Concrete field names like `marc_245_a` or `marc_245_ind1` should be interpreted dynamically when they appear in a query or field-selection request.

Reason:

- Returning every supported MARC field in `GET /entity-types/{id}` would create a very large column list.
- That would be expensive, noisy, and hard for clients to work with.

Important consequence:

- Clients cannot rely on the normal entity type response to enumerate every MARC field.
- For the POC, this is acceptable.
- If better discovery is needed later, it should probably be handled through a separate MARC field discovery/help mechanism rather than eager expansion into the normal entity type response.

### 8. `matched_id` is expected to identify one current SRS record

The current SRS assumption behind the `simple_srs_record` model is that `matched_id` identifies the logical record we want FQM to treat as one SRS record.

This assumption has been confirmed with the `mod-source-record-storage` team at least for current non-`OLD` records in these states:

- `ACTUAL`
- `DELETED`

Specifically:

- only one `ACTUAL` or `DELETED` record should exist for a given `matched_id`

Why this matters:

- `simple_srs_record` currently uses `matched_id` as the entity ID column
- the dynamic MARC query logic correlates `marc_indexers` rows through `record_lb.matched_id`
- duplicate current rows for the same `matched_id` would break the intended one-record-per-`matched_id` model

Current interpretation:

- duplicate `ACTUAL` rows for one `matched_id` should be treated as upstream data anomalies, not as a normal case this MARC work needs to support

Open follow-up:

- we should still confirm whether a `DRAFT` record can coexist with an `ACTUAL` or `DELETED` record for the same `matched_id`
- current expectation is that `DRAFT` should behave the same general way and not introduce a second current row for the same `matched_id`, but that has not been explicitly confirmed yet
- until that is confirmed, we should avoid claiming a stronger invariant than “at most one `ACTUAL`/`DELETED` row per `matched_id`”

## Explicitly Out of Scope for This POC

The following are intentionally not part of the first implementation pass:

### 1. Occurrence-aware correlation within repeated MARC fields

Example:

- `marc_650_a = 'Nuclear energy' AND marc_650_ind2 = '7'`

This does **not** guarantee that both conditions matched the same `650` occurrence.

That is a broader FQM limitation for repeatable structured data, not something unique to MARC.

Important data constraint discovered during the spike:

- `marc_indexers` does not carry a per-occurrence discriminator column
- for example, two `035 $a` subfields on the same record are stored as two rows that are identical except for `value`, with no column that distinguishes one `035` occurrence from another:

```
"035" "#" "#" "a" "(OCoLC)914463940"     <marc_id> 0
"035" "#" "#" "a" "(OCoLC)ocn914463940"  <marc_id> 0
```

- the trailing column is `0` on both rows, so it is not an occurrence index
- consequently, same-occurrence correlation **cannot be solved against `marc_indexers` alone**
- a future implementation would need to correlate against the MARC `content` JSONB blob (or a schema that adds an occurrence key)

Comparable existing limitation:

- repeatable structured fields like `alternative_titles.title` and `alternative_titles.title_type` are currently evaluated independently at the record level

Current assumption:

- The MARC POC should follow current FQM repeatable-field semantics rather than solve same-occurrence correlation.

### 2. Exact reconstructed full-field string matching

Not in scope:

- rebuilding a canonical display string for a field like `700 12 $a Shakespeare, William, $d 1564-1616. $t Antony and Cleopatra. $l Japanese`
- exact phrase matching against that reconstructed string

Current assumption:

- tag-level searching means "search anywhere in the values of this tag", not "reconstruct the whole MARC field and match its formatted representation"

### 3. `006` / `007` / `008` byte-position support

Not part of the first POC unless explicitly added later.

### 3a. Leader support

Leader querying is also not part of the current first-pass grammar.

It is worth calling out separately from `006` / `007` / `008` because:

- leader is still an important MARC requirement area
- leader positions are conceptually simpler than fixed-position byte ranges in `006` / `007` / `008`
- but leader does not fit the current tag / indicator / subfield grammar and has not yet been prototyped in this spike

Current assumption:

- leader support should be treated as a follow-up extension with its own naming/query proposal
- it should not be implied to already work just because the generic MARC capability exists

### 4. Full MARC validity-independent coverage

Not part of the first POC:

- every possible tag
- every possible subfield
- every invalid coded value

Current assumption:

- The POC should support syntactically valid names within the agreed MARC grammar, rather than enumerating a configured field list up front.

### 5. Regex / advanced wildcard semantics

The POC should not try to solve:

- true regex behavior
- embedded wildcard semantics beyond what existing FQM operators already provide

## Proposed Generic MARC Placeholder Contract

The placeholder should be simple and act only as a declaration that the entity type supports dynamic MARC field references.

Example shape:

```js
{
  name: 'marc',
  labelAlias: 'MARC',
  dataType: {
    dataType: 'marcDataType'
  },
  queryable: true,
  hidden: true,
  visibleByDefault: false,
  essential: true,
  valueGetter: ':record_lb.matched_id',
}
```

Notes:

- The placeholder `valueGetter` is being used as a correlation hint to `marc_indexers`, not as a user-facing display getter for the generic `marc` field itself. (Leader correlation to `marc_indexers_leader` is not yet implemented and would be a follow-up.)
- This keeps the placeholder contract within the existing `Field` / `EntityTypeColumn` model and avoids needing extra metadata on `marcDataType`, though it may still be refined later.
- At the moment, no separate expansion config is assumed to be necessary for the POC.

## Query Semantics Assumptions

### Tag columns

Examples:

- `marc_245`
- `marc_700`

Meaning:

- query/display the values associated with the tag, without requiring subfield or indicator targeting
- display should aggregate matching values
- query semantics should check whether at least one matching MARC row satisfies the operator/value

### Indicator columns

Examples:

- `marc_245_ind1`
- `marc_246_ind2`

Meaning:

- query/display indicator values for that tag
- display should aggregate distinct indicator values
- query semantics should treat indicator values as membership checks, e.g. `marc_245_ind1 = '1'` means "indicator 1 has value 1 for this tag"

### Subfield columns

Examples:

- `marc_245_a`
- `marc_650_a`

Meaning:

- query/display the values for that subfield within that tag
- display should aggregate matching subfield values
- query semantics should check whether at least one matching MARC row satisfies the operator/value

### Constrained subfield columns

Examples:

- `marc_245_ind1_7_a`
- `marc_650_ind2_7_a`

Meaning:

- query/display the subfield value for that tag
- only consider rows whose indicator matches the baked-in indicator value
- display should aggregate matching subfield values
- query semantics should check whether at least one matching MARC row satisfies both the fixed indicator constraint and the user-provided operator/value

### Negation and empty semantics

Because MARC filtering is implemented with correlated `EXISTS` / `NOT EXISTS` against `marc_indexers`, negation and empty operators have semantics worth calling out explicitly:

- `$ne` and `$nin` compile to `NOT EXISTS (... value = X)`, which means "no MARC row for this selector has this value", not "a MARC row exists with a different value"
- as a result, a record that does not have the tag/subfield at all will **match** a `$ne` query (the condition is vacuously true)
- `$empty: true` is the absence/empty case (no matching non-empty row), and `$empty: false` is presence; `$empty` also treats empty-string values (`<> ''`) as empty
- this differs from intuition for multi-valued data and should be mirrored in UI guidance and backend validation, so users understand that "not equal" includes "not present"

### Current POC grammar

The current grammar supports:

- `marc_<tag>`
- `marc_<tag>_ind1`
- `marc_<tag>_ind2`
- `marc_<tag>_<subfield>`
- `marc_<tag>_ind1_<indicatorValue>_<subfield>`
- `marc_<tag>_ind2_<indicatorValue>_<subfield>`

Examples:

- `marc_001`
- `marc_245`
- `marc_245_ind1`
- `marc_245_a`
- `marc_245_ind1_7_a`

Not part of the current grammar:

- leader position syntax
- `006` / `007` / `008` byte-position syntax

## Dynamic Resolution Assumption

Current preferred direction:

- if a query references `marc_245`, `marc_245_ind1`, or `marc_245_a`, the backend should parse that field name and synthesize the corresponding `EntityTypeColumn` on demand
- this should happen in the query path rather than by preloading every possible MARC field into the entity type definition

This means the main implementation question is not "how do we expand a huge list of MARC columns up front?"

It is:

- "where in the query pipeline should a valid MARC field name be recognized and turned into a synthetic column definition?"

## Implementation Constraints Already Identified

### 1. DTO model is shared

Entity type DTOs come from `org.folio.querytool.domain.dto`, so any `marcDataType` behavior needs to fit that model cleanly.

### 2. `simple_srs_record` does not currently include a `marc_indexers` source

Current assumption:

- For the POC, dynamically synthesized getters can use correlated subqueries directly against `marc_indexers`
- We do not need to model `marc_indexers` as a normal entity source first
- Leader querying against `marc_indexers_leader` is out of scope here; if added later it would follow the same correlated-subquery pattern

### 3. Indicators may duplicate on display

Because indicators may be represented alongside subfield rows in indexed data, indicator-only display values may need deduplication.

Current assumption:

- This is an implementation detail to solve in getter generation, not a reason to complicate the contract

### 4. Performance should be judged by platform impact, not by requiring every MARC query to be fast

Current understanding:

- `marc_indexers` is normalized, which is enough to make the dynamic MARC approach logically feasible
- we should not assume that additional indexes will be available on that table
- because of that, the main performance question is not "can this be perfectly optimized?"
- it is "can we support this query model without causing unacceptable impact to the rest of FOLIO?"

Current assumption:

- it is acceptable if some MARC queries on this entity type are slow against very large datasets
- it is not acceptable if those queries materially degrade the performance or stability of other FOLIO apps
- performance validation should therefore focus on overall database/platform impact, not only on whether the MARC entity type itself is fast

Likely implications:

- operator choice matters, especially broad text `contains` searches
- query shape matters more than whether the table is merely normalized
- if indexes are unavailable, "do the best with what we have" is the right framing for implementation
- any implementation recommendation should call out scale risk honestly rather than treating lack of indexes as a blocker

## Current Likely Code Touchpoints

These are the likely places the POC will need changes:

- [`EntityTypeRepository.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/repository/EntityTypeRepository.java)
  - possible utility location for MARC field synthesis logic
- [`EntityTypeValidationService.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/service/EntityTypeValidationService.java)
  - placeholder validation and/or dynamic field validation
- [`FqlToSqlConverterService.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/service/FqlToSqlConverterService.java)
  - MARC query semantics
- [`ResultSetRepository.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/repository/ResultSetRepository.java)
  - result handling / multi-valued display behavior for synthetic MARC fields
- [`EntityTypeService.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/service/EntityTypeService.java)
  - operator exposure / field metadata behavior / dynamic field handling
- [`EntityTypeFlatteningService.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/service/EntityTypeFlatteningService.java)
  - datatype handling in flattening
- [`LocalizationService.java`](/Users/bsharp/workspace/mod-fqm-manager/src/main/java/org/folio/fqm/service/LocalizationService.java)
  - datatype handling in localization

## Open Questions

1. What exact metadata fields should live under the generic placeholder `marcDataType`?
2. Should leader positions use the same naming grammar, and if so what should it be?
3. Do we want to tolerate raw `#` as a backward-compatible input in direct queries, or document only `blank`?
4. Where exactly should dynamic MARC field recognition happen in the request/query pipeline?
5. Do we want the first implementation pass to include one leader-position example, or leave leader support for the second pass?
6. What guardrails, if any, do we want if very broad MARC searches prove expensive at large scale?

## Current Recommended Next Steps

The POC has now proved the dynamic-field approach end to end for core MARC cases. The next steps should focus on turning that into a team-ready recommendation and implementation plan:

1. Summarize the spike recommendation for team review.
   The recommendation should be: one generic MARC capability with dynamic field resolution at query time, not thousands of eager predefined fields.

2. Confirm the MVP support boundary.
   This should explicitly call out what is supported now:
   - tag only
   - indicator only
   - subfield only
   - constrained subfield with fixed indicator value
   This should also explicitly call out what remains separate:
   - same-repeatable-entry correlation across multiple predicates
   - combined `ind1 + ind2` guarantees
   - leader / `006` / `007` / `008`
   - blank-indicator UI/alignment work

3. Review the approach with the development team and SA.
   The main goal should be to validate that the internal synthetic-field grammar is an acceptable backend contract and that the documented limitations are understood.

4. Do focused performance validation with the system we actually have.
   This should test representative tag, subfield, indicator, and constrained-subfield queries against realistic data volumes, with special attention to:
   - broad `contains` searches
   - repeated correlated `EXISTS` predicates
   - whether query load has unacceptable impact beyond this entity type

5. Define the query-builder / UI implications.
   The current backend approach assumes the UI will build MARC selectors dynamically rather than choosing from a giant preloaded dropdown. That needs an explicit UI story and likely a MARC-specific field builder interaction.

6. Create implementation stories from the proven POC.
   At minimum these should cover:
   - backend hardening for dynamic MARC querying
   - performance validation / mitigation
   - UI/query-builder support
   - blank indicator UI/alignment work
   - leader and fixed-position follow-up work
   - same-repeatable-entry correlation as a separate cross-cutting story
