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

### 5. Indicators are not a special contract category

Indicators should be treated similarly to tags and subfields at the contract level.

That means:

- `marc_245_ind1` simply means "query/display indicator 1 for tag 245"
- `marc_245_ind2` simply means "query/display indicator 2 for tag 245"

Current assumption:

- Indicator support does not require special condition syntax in the generated column name for this POC.

### 6. Tag + subfield + indicator is currently a broader semantics question

We have discussed a possible desire to support a shape like:

- tag + subfield + indicator

However, the important distinction is whether this means:

- "query/display the indicator itself" or
- "query/display a subfield value, but only when the same MARC row or occurrence has a matching indicator value"

The second case is where the simple dynamic-field model starts to break down.

A normal subfield query has one natural query value:

- `marc_245_a contains 'abc'`

The queried value is the subfield value itself.

A hypothetical subfield+indicator query would need two separate pieces of query data:

- the subfield value, for example `contains 'abc'`
- the indicator constraint, for example `ind2 = '7'`

That is awkward in the current field/operator/value model because only one of those can be the actual query value. If we try to encode the indicator value into the field name, the field name stops meaning "which value are we targeting" and starts carrying part of the predicate.

Example of the kind of shape we do **not** want to rely on:

- `marc_650_a_ind2_7 contains 'Nuclear energy'`

This is possible in principle, but it is a messy contract because:

- the field name is now mixing selector information with a filter condition
- the actual query value only covers the subfield content, not the indicator constraint
- it does not generalize well to richer same-occurrence cases
- it makes the dynamic naming grammar harder to explain and harder to trust

Current assumption:

- The first case is already covered by normal indicator columns like `marc_245_ind1`.
- The second case should not be forced into the current grammar by making the field name longer.
- If we ever need this later, it should probably use a MARC-specific query structure or additional predicate metadata rather than a field-name hack.

This also overlaps with the broader repeatable-field correlation story. Even if we solved the "subfield value plus indicator value on the same row" case, that still would not solve the more general "multiple queried values must all match within the same repeatable occurrence" problem across multiple MARC rows.

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

### Current POC grammar

The first grammar pass should support:

- `marc_<tag>`
- `marc_<tag>_ind1`
- `marc_<tag>_ind2`
- `marc_<tag>_<subfield>`

Examples:

- `marc_001`
- `marc_245`
- `marc_245_ind1`
- `marc_245_a`

Not part of this first grammar pass:

- `marc_<tag>_<subfield>_<indicator condition>`
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
5. Do we want the first POC to include one leader-position example, or leave leader support for the second pass?

## Current Recommended Next Step

Lock the generic MARC column contract before touching implementation:

1. placeholder metadata shape
2. generated naming grammar
3. exact first set of MARC field name patterns to support in the POC
4. where dynamic field synthesis should happen

Once those are settled, the implementation work becomes much more straightforward.
