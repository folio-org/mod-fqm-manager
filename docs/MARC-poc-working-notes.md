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
- Dynamically resolved MARC fields can query MARC data from `marc_indexers` / `marc_indexers_leader`.
- Dynamically resolved MARC fields can display MARC values in query results.
- Dynamic MARC fields work with existing FQM query behavior and existing composite-entity query composition.

## Current Working Assumptions

### 1. `marcDataType` exists

`marcDataType` has already been added and is available for use in this project.

The current working expectation is:

- MARC fields display like multi-valued MARC data.
- MARC fields query via MARC-specific row-level `EXISTS` / `NOT EXISTS` semantics.
- MARC fields may expose a more restricted operator set than normal array-like fields.

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

### 5. Indicators are not a special contract category

Indicators should be treated similarly to tags and subfields at the contract level.

That means:

- `marc_245_ind1` simply means "query/display indicator 1 for tag 245"
- `marc_245_ind2` simply means "query/display indicator 2 for tag 245"

Current assumption:

- Indicator support does not require special condition syntax in the generated column name for this POC.
- Indicator positions are limited to `ind1` and `ind2`, which is the complete MARC indicator model we need to support.
- Fixed indicator values in constrained-subfield fields are currently modeled as single-character alphanumeric values.
- Uppercase indicator input should be accepted and normalized internally, so `MARC_245_IND1` behaves the same as `marc_245_ind1`.
- Blank-indicator encoding is still an open design decision and is not part of the current grammar.

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
- `marc_650_ind2_7_a`

Current assumption:

- The constrained indicator comes before the subfield.
- The last token still identifies the value-bearing target.
- We do not need the reverse shape, because the point of the field is still to query the subfield value while applying a fixed indicator constraint.
- This should be treated as a targeted extension to the MARC grammar, not as a general solution for repeatable-field correlation.
- The current implementation expects the baked-in indicator value and the subfield code to each be a single alphanumeric character.

Approximation we can already make:

- `marc_245_ind1_7_a = 'xyz' AND marc_245_ind2_7_a = 'xyz'`

This will often behave the way a user expects, especially for nonrepeatable tags or when the subfield value is highly specific. In many real records, it is more likely that both conditions refer to the same MARC row than that two different rows happen to share the same subfield value under different indicators.

However, it is still only an approximation. The query means:

- there exists a row where `ind1 = '7'`, `subfield = 'a'`, and `value = 'xyz'`
- and there exists a row where `ind2 = '7'`, `subfield = 'a'`, and `value = 'xyz'`

Those two matches could still come from different rows. So this is useful in practice, but it should not be documented as guaranteeing a combined `ind1 + ind2` same-row match.

This also overlaps with the broader repeatable-field correlation story. Even if we solved the "subfield value plus indicator value on the same row" case, that still would not solve the more general "multiple queried values must all match within the same repeatable occurrence" problem across multiple MARC rows.

How this relates to the broader same-entry story:

- the combined `ind1 + ind2` case should be treated as part of that broader repeatable-entry problem
- a future implementation of same-entry correlation should also be expected to handle this MARC case
- the current `marcDataType` / `EXISTS` approach is compatible with that direction
- the future solution would likely need to combine compatible MARC predicates into one row-level `EXISTS` rather than evaluating them as separate independent `EXISTS` clauses

Remaining boundaries of this approach:

- multiple subfield predicates that must all match within the same repeatable MARC occurrence are still not solved
- guaranteed combined `ind1 + ind2` same-row constraints are still not solved by the current implementation
- blank indicator handling still needs an explicit design decision for both field naming and query behavior

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

## Explicitly Out of Scope for This POC

The following are intentionally not part of the first implementation pass:

### 1. Occurrence-aware correlation within repeated MARC fields

Example:

- `marc_650_a = 'Nuclear energy' AND marc_650_ind2 = '7'`

This does **not** guarantee that both conditions matched the same `650` occurrence.

That is a broader FQM limitation for repeatable structured data, not something unique to MARC.

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

- The placeholder `valueGetter` is being used as a correlation hint to `marc_indexers` / `marc_indexers_leader`, not as a user-facing display getter for the generic `marc` field itself.
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

- For the POC, dynamically synthesized getters can use correlated subqueries directly against `marc_indexers` / `marc_indexers_leader`
- We do not need to model `marc_indexers` as a normal entity source first

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
3. How should blank indicator values be represented in display and querying?
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
   - blank-indicator semantics

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
   - blank indicator semantics
   - leader and fixed-position follow-up work
   - same-repeatable-entry correlation as a separate cross-cutting story
