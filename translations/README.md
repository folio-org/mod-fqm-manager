## Translation guidelines

### General guidelines

When possible, please use the following guidelines when translating field names:

- All labels should be in sentence case, with only the first word capitalized.
  - _Example_: `First name` instead of `First Name` or `first name`
  - _Exception_: Qualified names that use an em-dash for heirarchy should use sentence case within each component
    - _Example_: `User record — First name`
- Entity type names should **not** be included in the label, when it is redundant
  - _Example_: `First name` instead of `User first name`
- Field names should have only their first word capitalized, per FOLIO UI guidelines
  - _Example_: `First name`, `Effective call number`
- Identifier acronyms such as ID, HRID, etc., should always be fully capitalized
- Whenever handing UUIDs, call it a `UUID`. All other IDs should be referred to as their specific name.
  - _Example_: Instances will have an `Instance UUID` AND an `Instance HRID`. Neither should be called just `ID`, since that is ambiguous.
- Avoid using abbreviations/acronyms, unless they are used universally within their application
  - _Example_: `HRID` in lieu of `Human readable identifier` because `HRID` is used universally within the inventory applications, and `Human readable identifier` is never used
  - _Example_: `ISBN` in lieu of `International standard book number`, for the same reason
  - _Example_: `POL` should be abbreviated instead of `Purchase order line`
  - _Example_: **avoid** using `Acq. unit` in lieu of `Acquisition unit`
- Do not refer to arrays as 'lists' directly; instead, use plurals
  - _Example_: `Statistical codes` instead of `Statistical code list`, `Country IDs` instead of `Country ID list`
- For fields that build on another field's UUID, disambiguate each with 'UUID' and a unique description
  - _Example_: `Material type name` and `Material type UUID`
  - **Incorrect**: `Material type` and `Material type UUID`
- The same applies for lists
  - _Example_: `Acquisition unit UUIDs` and `Acquisition unit names`
- To represent hierarchy in names (for example, a outer entity type name + an inner field name), an em-dash should be used (`—`).
