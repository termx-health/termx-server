# TermX change summary — one page (business)

**Period:** 1 Jan 2025 – 24 Mar 2026 (local git history). **~165** termx-server commits, **~195** termx-web commits.  
**Scope:** Thematic summary from merge titles and branch names; not an exhaustive commit list.

**Other inputs:** (1) **MNKV Termix tasks** spreadsheet (March 2026)—client numbered tasks, estimates, and test notes; not in repo. (2) Internal product docs under `[docs/features/](features/)` (ecosystem API/UI, **code system supplement** & **$lookup**, task access, import emails, value-set rule preview).

## 2026 priorities (snapshot)

- **API & data:** Richer **reference payloads** (e.g. **display** on coded references); routing may involve **htx-router**. **Version visibility** via server config (full **UI** for all toggles still evolving).
- **UCUM & supplements:** **Import/export**, **translations** via **supplements**, **case** semantics; **export** respects UCUM’s “computed” nature (finite subsets / merged supplements). **Full support** for FHIR **CodeSystem** **supplements** and **$lookup** with supplement merge (explicit `**useSupplement`** or `**displayLanguage**` auto-discovery)—see [code-system-supplement-and-lookup.md](features/code-system-supplement-and-lookup.md).
- **Performance & UX:** **Hierarchical code system** browsing under load; **filter-in-tree** and **persistent import mappings** on the UX backlog.
- **Workflows:** **ValueSet** editing/preview (**dynamic** vs **static**, **rule preview**—aligned with [value-set-rule-expansion-preview](features/value-set-rule-expansion-preview.md)); **ConceptMap**/**CodeSystem** cross-navigation and **task** visibility by **role** ([task-access-control](features/task-access-control.md)).
- **Imports & notifications:** **Email** on long-running imports (**LOINC**, **SNOMED**, file imports, etc.—[email-import-notifications](features/email-import-notifications.md)); **CSV/XLSX** export/import with ongoing **round-trip** hardening per spreadsheet notes.
- **Integration:** **Token/refresh** behaviour in **embedded** MNKV vs standalone TermX—retest where noted in the register.

**Effort rollup (spreadsheet):** on the order of **~268** hours / **~33** calendar days estimated across listed items (capacity interpretation varies).

## termx-server

- **Terminology / FHIR:** Richer **value set** behaviour (designation **pattern** search; **inline expand**, **rule expansion preview**, **lookup** enhancements); **validation** by name/code/display; **UCUM** / units on FHIR paths; **full** **code system supplement** support and **$lookup** with supplements ([code-system-supplement-and-lookup.md](features/code-system-supplement-and-lookup.md)).
- **Platform:** **Java 25**, **API** and **package** refactors; **build/release** and **container registry** publishing; **Sonar** / **SpotBugs**.
- **Scale & ops:** **Memory** and **code-system** performance; faster **exports**; **import/export**; **SMTP**-related import; **task** permissions / **taskforge**.
- **Hygiene:** **URL** configuration instead of hardcoded links.
- **Ecosystem:** **FHIR Terminology Ecosystem** discovery/resolution API and optional **web UI**—see [fhir-terminology-ecosystem-feature-description.md](features/fhir-terminology-ecosystem-feature-description.md). **HTX demos:** [router](https://tx.hl7.lt) · [viewer](https://htx.hl7.ee).

## termx-web

- **UX:** **Readonly/detailed** views and **metadata** consistency; **large diff** screens; **error** messaging improvements; **tree** and **list** performance.
- **Terminology:** **UCUM** UI; **concept reference** widget; **rule expansion**; alignment with **TERMX** / **TEABH** delivery.
- **Integration:** **Koodivaramu** merge line and related **UI** fixes; routine **upstream** syncs.

## Headline

A **steady 2025** foundation of fixes and TERMX/TEABH delivery, with a **strong early-2026 push** on **platform modernisation**, **quality tooling**, **terminology depth**, and **joint web/server** features (especially **March 2026** on the server side). The **MNKV** task register emphasises **governance**, **spreadsheet round-trips**, and **embedded integration** alongside those product themes.

---

**Full narrative:** [business-change-summary-2025-2026-full.md](business-change-summary-2025-2026-full.md).