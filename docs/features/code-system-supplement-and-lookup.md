# Code System Supplement and $lookup Operation

**Feature:** FHIR CodeSystem supplements and `$lookup` operation with supplement support  
**Based on:** Development performed in February–March 2026 (excluding 2026-03-09)

---

## 1. Code System Supplement

### Overview

A **Code System Supplement** is a FHIR CodeSystem resource with `content = "supplement"` that adds designations (e.g. translations) and/or properties to concepts of a **base** code system, without duplicating the full concept set. The supplement references the base via the `supplements` element (canonical URL of the base CodeSystem).

Typical use cases:

- **Localisation:** Add display names and synonyms in another language (e.g. Lithuanian UCUM).
- **Extra properties:** Add organisation-specific or regional properties to an existing code system.

### Model in TermX

- A supplement is stored as a **separate CodeSystem** with:
  - `content = "supplement"`
  - `baseCodeSystem` (internal ID) / `baseCodeSystemUri` set to the base code system.
- Concepts in the supplement use the **same codes** as in the base system; they only add designations and/or property values for those codes.
- Supplements are linked to the base by `baseCodeSystem`; the base is resolved by URI (e.g. `http://unitsofmeasure.org` for UCUM).

### Creating a Supplement

- **Full supplement (new supplement CodeSystem):** Use the supplement API with `CodeSystemSupplementRequest` specifying `codeSystem` (ID) and `codeSystemUri` (canonical URL). The service creates a new CodeSystem with `content = "supplement"` and `baseCodeSystem` set from the given base.
- **Concept-level supplement:** Add entity versions to an existing supplement CodeSystem via the supplement API with `ids` (base entity version IDs) or `externalSystemCode` (code from the base system); designations and properties from the base are copied and can be localised or extended.

### Example: UCUM Lithuanian Supplement

The Lithuanian UCUM supplement adds Lithuanian (lt) display names and aliases to UCUM unit codes. Example resource (as published):

- **Canonical URL:** `https://tx.hl7.lt/CodeSystem/ucum` (or variant with version).
- **Versioned instance (HTX example):** [ucum-lt--0.0.3](https://htx.helex.dev/lmb-api/fhir/CodeSystem/ucum-lt--0.0.3)
- **FHIR structure:**
  - `content`: `"supplement"`
  - `supplements`: `"http://unitsofmeasure.org"`
  - `concept[]`: same codes as in UCUM (e.g. `kPa`, `pg/mL`, `mmol/L`, `mL`), each with `designation[]` for language `lt` (display and/or alias).

Example concept from the supplement:

```json
{
  "code": "mmol/L",
  "display": "MilliMolesPerLiter",
  "designation": [
    { "language": "lt", "use": { "code": "display" }, "value": "Milimoliai litre" },
    { "language": "lt", "use": { "code": "alias" }, "value": "mmol/l" }
  ]
}
```

---

## 2. $lookup Operation on Supplements

### Overview

The FHIR **CodeSystem $lookup** operation returns details for a single concept (code + system, optionally version). TermX extends this with **supplement support**: designations (and in future, properties) from nominated supplements are merged into the response so that a single lookup can return both base and localised displays.

### Parameters (supplement-related)

| Parameter          | Use   | Type     | Description |
|--------------------|-------|----------|-------------|
| `system`          | in    | uri      | Base code system (e.g. `http://unitsofmeasure.org`). Required. |
| `code`            | in    | code     | Concept code. Required. |
| `version`         | in    | string   | Optional version of the code system. |
| `displayLanguage` | in    | code     | Preferred language for display (e.g. `lt`). Used for display selection and for **auto-discovery** of supplements (see below). |
| `useSupplement`   | in    | canonical| **0..*** Supplement(s) to apply. Canonical URL, optionally with version (e.g. `https://tx.hl7.lt/CodeSystem/ucum-lt` or `https://tx.hl7.lt/CodeSystem/ucum-lt|0.0.3`). |

### Supplement resolution

- **Explicit:** If the client sends one or more `useSupplement` parameters, the server uses those supplements (by resolving canonical to a stored CodeSystem with the same base).
- **Auto-discovery:** If `displayLanguage` is present and no `useSupplement` is sent, the server discovers supplements whose **base** is the lookup code system; designations from those supplements are included when their language matches `displayLanguage` (exact or language-range match, e.g. `lt` or `lt-LT`).
- Combined: Explicit `useSupplement` and auto-discovered supplements are merged (deduplicated by supplement reference); designations from all of them are then filtered by `displayLanguage` and returned.

### Response

- **name:** Code system name (base).
- **version:** Version used (if applicable).
- **display:** Preferred display for the concept (from base + supplements, according to `displayLanguage`).
- **designation:** One out parameter per additional designation; each has parts **use** (Coding), **value** (string), **language** (code). Supplement-origin designations appear here (e.g. Lithuanian display and alias for UCUM).

Designations from supplements are merged with the base concept’s designations; the main `display` is chosen using the usual display rules with the requested language.

---

## 3. Test Examples

### Example 1: $lookup with explicit UCUM Lithuanian supplement

**Request:** Type-level `$lookup` (no instance ID), UCUM system, code `mmol/L`, requesting Lithuanian via supplement.

```http
POST /fhir/CodeSystem/$lookup
Content-Type: application/json
```

**Body (Parameters):**

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "system", "valueUri": "http://unitsofmeasure.org" },
    { "name": "code", "valueCode": "mmol/L" },
    { "name": "displayLanguage", "valueCode": "lt" },
    { "name": "useSupplement", "valueCanonical": "https://tx.hl7.lt/CodeSystem/ucum-lt|0.0.3" }
  ]
}
```

**Expected behaviour:** Server resolves the base UCUM concept for `mmol/L`, loads the supplement `ucum-lt` (version 0.0.3 if supported), and returns:

- `display`: either the base display or the supplement’s lt display (e.g. `"Milimoliai litre"`) depending on display preference.
- `designation`: at least one entry with `language` = `lt` and value `"Milimoliai litre"` (display), and optionally alias `"mmol/l"`.

(If the server uses a single version or no version in canonical, the same can be tried with `valueCanonical": "https://tx.hl7.lt/CodeSystem/ucum-lt"`.)

---

### Example 2: $lookup with displayLanguage only (auto-discover supplement)

**Request:** Same operation, no `useSupplement`; only `displayLanguage` = `lt`.

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "system", "valueUri": "http://unitsofmeasure.org" },
    { "name": "code", "valueCode": "mL" },
    { "name": "displayLanguage", "valueCode": "lt" }
  ]
}
```

**Expected behaviour:** Server finds the UCUM concept for `mL`, discovers supplements whose base is UCUM (e.g. ucum-lt), and returns base display plus designations from the supplement for language `lt` (e.g. display “Mililitras”, alias “ml”). This matches the behaviour tested in `CodeSystemUcumOperationsTest.groovy` (“lookup auto-loads supplements by displayLanguage”).

---

### Example 3: Instance-level $lookup on the supplement resource (HTX-style URL)

When the CodeSystem resource is exposed by instance (e.g. `ucum-lt--0.0.3`), the same parameters can be sent to the instance operation. The server typically still performs lookup against the **base** system (UCUM) plus the indicated supplement, so the request can look like:

**Request:**

```http
POST /fhir/CodeSystem/ucum-lt--0.0.3/$lookup
Content-Type: application/json
```

**Body:**

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "system", "valueUri": "http://unitsofmeasure.org" },
    { "name": "code", "valueCode": "kPa" },
    { "name": "displayLanguage", "valueCode": "lt" }
  ]
}
```

**Expected:** Concept `kPa` in UCUM with Lithuanian display from the supplement (e.g. “Kilopaskalis”), plus any other designations for `lt`.

---

### Example 4: Validate-code with supplement (designation from supplement)

**Request:** `$validate-code` with a display that comes from the supplement (e.g. Lithuanian abbreviation “ml” for `mL`).

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "url", "valueUrl": "http://unitsofmeasure.org" },
    { "name": "code", "valueCode": "mL" },
    { "name": "display", "valueString": "ml" },
    { "name": "displayLanguage", "valueCode": "lt" },
    { "name": "useSupplement", "valueCanonical": "https://tx.hl7.lt/CodeSystem/ucum-lt|0.0.3" }
  ]
}
```

**Expected:** `result` = true, because “ml” is a valid designation (e.g. alias) for `mL` in the Lithuanian supplement. This aligns with the test “validate-code accepts UCUM supplement abbreviation as display” in `CodeSystemUcumOperationsTest.groovy`.

---

## 4. Reference: UCUM Lithuanian supplement (HTX)

- **Instance (versioned):** [https://htx.helex.dev/lmb-api/fhir/CodeSystem/ucum-lt--0.0.3](https://htx.helex.dev/lmb-api/fhir/CodeSystem/ucum-lt--0.0.3)
- **Content:** Supplement to `http://unitsofmeasure.org`; adds Lithuanian (lt) designations for many UCUM codes (e.g. kPa → “Kilopaskalis”, mmol/L → “Milimoliai litre”, mL → “Mililitras”, % → “Procentas”).
- Use this URL (with or without `|0.0.3`) in `useSupplement` for the examples above when testing against an environment that hosts this resource.

---

## 5. Implementation notes (TermX codebase)

- **Lookup + supplements:** `CodeSystemLookupOperation` loads the concept from the base system, then calls `loadSupplementDesignations(...)` which:
  - collects supplement references from `useSupplement` parameters (valueCanonical/valueUri/valueUrl/valueString),
  - if `displayLanguage` is set, discovers supplements by `baseCodeSystem` + `content = supplement`,
  - for each supplement, loads the concept by the same `code` in the supplement and merges designations, filtered by `displayLanguage`.
- **UCUM special case:** For `system = "http://unitsofmeasure.org"`, the server maps to the internal code system ID `ucum` when the URI does not match a stored CodeSystem by URI.
- **Value set expansion:** Value set expansion can also include supplement designations for UCUM concepts when a preferred language is set, via `UcumSupplementDesignationService` (enriches `ValueSetVersionConcept` with `additionalDesignations` from UCUM supplements for the given language).
