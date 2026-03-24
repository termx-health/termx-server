# Change summary (business): 2025-01-01 through 2026-03-24

**Repositories:** termx-server and termx-web (sibling project).  
**Audience:** Non-technical stakeholders.  
**Language:** English.

### Activity pattern (high level)

- **termx-server:** In 2025 notable merges include **TERMX** features/fixes, validation improvements, URL handling, **FHIR UCUM** work toward **late 2025 / early 2026**. A **large concentration of platform and product work lands in March 2026** (Java/tooling, quality gates, performance, terminology/FHIR capabilities, import/export).
- **termx-web:** In 2025 upstream syncs, **TERMX** PRs, Integration of the **Teabekeskus** branches. **2026** adds **UCUM**, **readonly/detailed views**, **Koodivaramu** merge line, performance (tree view), **Taskforge**, UI and metadata fixes, **rule expansion** and **concept reference** UI - aligned with server evolution.

---

**Tasks**

The following distils **business-relevant** themes from the MNKV task list (20 numbered items). Status wording comes from the sheet (“Tested, DONE”, “TODO”, integration retest, etc.)—treat as a **snapshot**, not a formal sign-off.


| Theme                             | What the client asked for                                                                                                                                                                                                                                                                               |     |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --- |
| **API & references**              | Richer JSON when code systems reference each other (e.g. **display** next to system/code); routing work may involve **htx-router**.                                                                                                                                                                     |     |
| **Visibility of versions**        | Control which **CodeSystem / ValueSet / ConceptMap** versions are exposed via API (published vs draft; allow/deny specific versions).                                                                                                                                                                   |     |
| **Read-only configuration views** | View full configuration **without** entering edit mode (parity across CodeSystem / MapSet / etc.).                                                                                                                                                                                                      |     |
| **UCUM**                          | Stable **import**, **supplements** for translations, **case sensitivity** rules, **export** (understanding UCUM is partly computed—infinite concept space).                                                                                                                                             |     |
| **Code system supplement (FHIR)** | **Full support** for FHIR **CodeSystem** supplements (`content = supplement`): add **designations** (e.g. translations) and **properties** on a **base** system without duplicating concepts; **$lookup** merges supplement data with **explicit** or **language-based auto-discovery** of supplements. |     |
| **Performance**                   | Faster **hierarchical browser** for large or “wide” trees (lab nomenclatures, few parents / many children).                                                                                                                                                                                             |     |
| **Cross-links & workflow**        | From **CodeSystem**/**ConceptMap**/**ValueSet** UIs: show **version**, **concept status**, and **navigation** to edit referenced terms where permitted.                                                                                                                                                 |     |
| **ValueSet lifecycle**            | Clear **edit/approve** steps; **dynamic** vs **static** sets; **preview** of rule-based expansion (incl. SNOMED); optional **ECL-style** queries longer-term.                                                                                                                                           |     |
| **Staleness & impact**            | When upstream codes change, **notify** users and surface **status** / links to fix downstream content.                                                                                                                                                                                                  |     |
| **Import / export**               | **Email** progress and **errors** for long imports (e.g. SNOMED, LOINC); **CSV/XLSX** export and **round-trip** edit in Excel.                                                                                                                                                                          |     |
| **Tasks & roles**                 | **MNKV**-aligned **admin / publish / edit / view** roles; **task list** visibility rules (view-only users should not see tasks; editors see own/assigned; publishers see broader sets).                                                                                                                 |     |
| **Integration & UX**              | Reliable **authentication** in the **public terminology browser** (token refresh); ongoing **UI/UX** tweaks (e.g. **hierarchy filter**, **persist import mappings**).                                                                                                                                   |     |


**Effort snapshot (from the same spreadsheet):** the register rolls up to on the order of **~268** recorded hours and **~33** calendar days of estimated work across the line items (with **~16.75** “work-day” equivalents in one summary row—interpretation depends on team capacity assumptions).

---

## New important  features (docs/features)

These documents **do not replace** the MNKV spreadsheet or git history; they explain **how the product is intended to work** where delivery matches documented behaviour.

- **FHIR Terminology Ecosystem** — Discovery and resolution of terminology servers (HL7 coordination patterns), **REST** endpoints, and optional **web UI** under `/tx-ecosystem/`. See the index: [fhir-terminology-ecosystem-INDEX.md](features/fhir-terminology-ecosystem-INDEX.md) and overview: [fhir-terminology-ecosystem-feature-description.md](features/fhir-terminology-ecosystem-feature-description.md). **Example HTX demos:** [router (gateway)](https://tx.hl7.lt) · [viewer (browser)](https://htx.hl7.ee).
- **Code system supplement & $lookup** — **Full support** for FHIR **CodeSystem** supplements (localisation and extra properties on a **base** code system) and for **CodeSystem $lookup** with supplement merge: parameters `**useSupplement`** (explicit) and `**displayLanguage**` (auto-discovery of matching supplements). Development scope in the doc: February–March 2026. [code-system-supplement-and-lookup.md](features/code-system-supplement-and-lookup.md)
- **Value set rule expansion preview** — Server endpoint to **expand a single include rule** without saving the value set version first (`POST /ts/value-sets/expand-rule`), feeding the **rule editor preview** in the UI. [value-set-rule-expansion-preview.md](features/value-set-rule-expansion-preview.md)
- **Task access control** — **Role-based** visibility (**Admin** sees all; **Publisher** broad access on publishable resources; **Editor** own/assigned; **Viewer** no task list). [task-access-control.md](features/task-access-control.md)
- **Email import notifications** — **HTML** emails for import completion/failure across major import types (**LOINC**, **SNOMED**/Snowstorm, file imports, etc.), configurable recipients. [email-import-notifications.md](features/email-import-notifications.md) (with [smtp-email-support.md](features/smtp-email-support.md))
- **SMTP / task management (broader)** — [task-management.md](features/task-management.md) for TaskForge behaviour complementary to access control.

---

## Detailed summary

### termx-server (backend / terminology / platform)

**Terminology & FHIR**

- **Value set expansion:** Early **2025** (**r2.6**, PR #27): designation-name filtering extended with **pattern/regex-style** matching (not only exact equality)—richer search when building or expanding value sets.
- **Validation & operations:** Later **2025** merges include **validate-by-name**, **value set validate code/display name**, **operation definitions** contribution, and a **revert** on an operation-definition change—reflecting iterative refinement of FHIR-style validation and APIs.
- **UCUM & units:** **2026** brings **FHIR UCUM** implementation and a **UCUM switch** path, plus related ecosystem alignment (**implement-fhir-ucum-java**, **ucum-switch**).
- **Code system supplements:** **Full** FHIR-aligned support for **supplements** (separate `content = supplement` code systems tied to a base URI) and for **$lookup** that merges base concepts with supplement **designations**—including **explicit** supplement selection and **auto-discovery** by language. Primary documentation period **Feb–Mar 2026**; typical use cases include **national UCUM** display names and other **localisation** paths described in [code-system-supplement-and-lookup.md](features/code-system-supplement-and-lookup.md).

**Platform, build & quality (notably March 2026)**

- **Java 25** adoption, **package rename**, **API refactor**, and **optimisation** work—modernising the runtime and internal structure.
- **Build & release:** Faster builds (**build-speedup**), release pipeline work (**build-release**), publishing images (**publish2ghrc** / GitHub Container Registry).
- **Quality gates:** **Sonar** and **SpotBugs** integration—systematic static analysis for maintainability and defect prevention.

**Performance, scale & integrations**

- **Memory optimisation**, **code-system performance**, faster **export** paths (e.g. **speedup-vx-xslx-export**).
- **Task / permission** model (**taskforge1/2**, **task-permission**)—aligns with **task access control** documentation and MNKV role expectations.
- **SMTP**-related **import** work (**smtp1/smtp2import**)—pairs with **email import notifications**.
- **Import/export** (**impexp**) and **commons library**—shared building blocks and data movement.

**Product features (March 2026 merges)**

- **FHIR ecosystem** alignment; **inline value set expand**; **rule expansion preview**; **properties in lookup**—these improve how terminologies are explored, previewed, and wired into lookups.

**Configuration & hygiene**

- **Hardcoded URLs** addressed in **late 2025**—configurable endpoints/environments rather than fixed strings.

---

### termx-web (frontend)

**Early 2025**

- **Large diff viewing:** Thresholds for **space** and **release** diffs raised so **very large** payloads remain viewable before “do not render” fallback.
- **Code system UI:** Designation rows use a dedicated CSS class—layout/styling control in the concept editor.
- **Housekeeping:** Comment cleanup in the **structure definitions graph** (no user-visible behaviour).

**2025–2026 product work (representative)**

- **TERMX**-numbered PRs/features and **TEABH** branches (numerous fixes and features—view modes, error presentation, metadata, etc.)—ongoing product hardening and UX.
- **Readonly / detailed views** and related **metadata** fixes—clearer read-only experiences and consistent metadata display.
- **UCUM module** on the web side—paired with server unit-handling work.
- **Koodivaramu** merge line—integration with external **Koodivaramu** history (Estonian national code system context); includes **UI fixes** and conflict resolution merges.
- **Performance:** **Tree view** performance improvements; **code system list** fixes.
- **Terminology UX:** **Concept reference widget**, **rule expansion** UI—surfacing server capabilities in the browser.
- **Quality:** **Sonar**-driven cleanup on the front end.
- **Upstream / community:** Repeated **upstream/main** merges and occasional **third-party** merges (e.g. **mato9**)—staying current with shared lineages.

---

### Cross-project takeaway

From **2025-01-01** onward, development advances on three axes: **deeper FHIR/terminology behaviour** (validation, UCUM, **code system supplements**, value sets, previews), **operational scale** (performance, large diffs, exports, memory), and **engineering maturity** (Java upgrade, static analysis, build/release, container publishing). The **web** and **server** tracks **converge in early 2026** around **UCUM**, **supplement-based localisation**, **task/permission** flows, **rule/value-set** UX, and **integration** (e.g. Koodivaramu).

The **MNKV spreadsheet** adds a **client-priority lens**: it highlights **governance** (who sees which **versions** and **tasks**), **round-trip editing** (spreadsheet workflows), **integration hardening** (auth in embedded contexts), and **ongoing UX** work alongside the same **terminology** capabilities described in git and **docs/features**.

---

## Companion document

A one-page **A4-style** brief: [business-change-summary-2025-2026-a4.md](business-change-summary-2025-2026-a4.md).