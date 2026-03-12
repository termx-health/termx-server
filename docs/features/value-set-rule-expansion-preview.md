# Value set rule expansion preview

`POST /ts/value-sets/expand-rule` expands a single include rule without saving the value set version first.

Request body:

```json
{
  "valueSet": "test-rule-preview",
  "valueSetVersion": "1.0.0",
  "inactiveConcepts": false,
  "rule": {
    "type": "include",
    "codeSystem": "contact-point-use",
    "codeSystemVersion": {
      "id": 123,
      "version": "6.0.0"
    }
  }
}
```

Behavior:

- Loads the current value set and version for language and expansion context.
- Converts the incoming rule into an inline FHIR `ValueSet.compose.include`.
- Uses the existing inline `$expand` SQL path via `ValueSetVersionConceptRepository.expandFromJson(...)`.
- Decorates the result the same way as normal value set expansion and filters inactive concepts unless `inactiveConcepts=true`.

This endpoint is intended for the TermX rule editor preview button and returns a compact list of `ValueSetVersionConcept` items that the UI can render as code and display rows.
