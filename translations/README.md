## Translation guidelines

### General guidelines

When possible, please use the following guidelines when translating field names:

- Entity type names should **not** be included in the label, when it is redundant
  - _Example_: `First name` instead of `User first name`
- Field names should have only their first word capitalized, per FOLIO UI guidelines
  - _Example_: `First name`, `Effective call number`
- Acronyms such as POL, ID, HRID, etc., should always be fully capitalized
- As long as it is not ambiguous, use 'ID' instead of 'UUID'
- Do not refer to arrays as 'lists' directly; instead, use plurals
  - _Example_: `Statistical codes` instead of `Statistical code list`, `Country IDs` instead of `Country ID list`
- For fields that build on another field's ID, disambiguate each with 'ID' and a unique description
  - _Example_: `Material type name` and `Material type ID`
  - **Incorrect**: `Material type` and `Material type ID`
- The same applies for lists
  - _Example_: `Acquisition unit IDs` and `Acquisition unit names`
