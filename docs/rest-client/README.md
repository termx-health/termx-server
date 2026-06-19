# TermX FHIR terminology — REST Client suites

Ready-to-run [`REST Client`](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)
(`humao.rest-client`) request suites for the TermX FHIR terminology API. They double as living
examples you can share with integrators to verify a server behaves correctly.

## Suites

| File | What it covers |
|------|----------------|
| [`fhir-terminology-supplement.http`](fhir-terminology-supplement.http) | A base CodeSystem + Estonian/Russian **supplements**, **stored ("static") ValueSets** referencing them via the `valueset-supplement` extension, expanded with `displayLanguage`; `$validate-code`; and **inline** `$expand`. |

## Using a suite

1. Install the **REST Client** extension in VS Code.
2. Open the `.http` file and set the two variables at the top:
   - `@fhir` — your server's FHIR base URL, no trailing slash (e.g. `https://demo.termx.org/fhir`; some deployments expose it under `/api/fhir`).
   - `@token` — a Bearer token with write access (needed to create the CodeSystems/ValueSets).
     On a local dev server (`auth.dev.allowed=true`) you can use the shortcut `yupi{"privileges":["*.*.*"]}`; against a real server use an actual token.
3. Click **Send Request** above each request, **top to bottom** — the `SETUP` block creates the
   resources the later tests depend on.
4. Each request has an `# expect:` comment describing the correct response, so you can verify
   correctness at a glance.

The setup requests are idempotent (resources are upserted by id/url), so the whole suite can be
re-run safely.

## Good to know

- **Stored vs inline.** A *stored* ValueSet expands through the full pipeline — it honours
  `displayLanguage` and resolves the `valueset-supplement` extension, so supplement translations
  show up. *Inline* `$expand` (passing the ValueSet in the request body) also surfaces supplement
  translations, but the supplements aren't declared on the inline ValueSet, so you tell the server
  which to apply: pass `displayLanguage` to auto-discover supplements with that language (suite
  test #16), or name them explicitly with one or more `useSupplement` parameters (tests #17–18).
  A bare inline expand with neither returns only the base code system's own designations.
- **Cleanup.** The suite leaves its `tx-rest-demo-*` resources on the server. Cancel/retire them
  from the UI (or your own DELETE requests) if you don't want them to linger on a shared instance.
