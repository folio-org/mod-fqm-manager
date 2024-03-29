## Translation guidelines

### General guidelines

When possible, please use the following guidelines when translating field names:

- Entity type names should **not** be included in the label, when it is redundant
  - _Example_: `First name` instead of `User first name`
- Field names should have only their first word capitalized, per FOLIO UI guidelines
  - _Example_: `First name`, `Effective call number`
- Identifier acronyms such as ID, HRID, etc., should always be fully capitalized
- As long as it is not ambiguous, use 'ID' instead of 'UUID'
- Avoid using abbreviations/acronyms, unless they are used universally within their application
  - _Example_: `HRID` in lieu of `Human readable identifier` because `HRID` is used universally within the inventory applications, and `Human readable identifier` is never used
  - _Example_: `ISBN` in lieu of `International standard book number`, for the same reason
  - _Example_: **avoid** using `Acq. unit` in lieu of `Acquisition unit`
  - _Example_: **avoid** using `POL` instead of `Purchase order line`
- Do not refer to arrays as 'lists' directly; instead, use plurals
  - _Example_: `Statistical codes` instead of `Statistical code list`, `Country IDs` instead of `Country ID list`
- For fields that build on another field's ID, disambiguate each with 'ID' and a unique description
  - _Example_: `Material type name` and `Material type ID`
  - **Incorrect**: `Material type` and `Material type ID`
- The same applies for lists
  - _Example_: `Acquisition unit IDs` and `Acquisition unit names`
