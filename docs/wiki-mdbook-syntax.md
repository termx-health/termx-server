# TermX Wiki ↔ mdbook: shared syntax

Authoritative "write it this way" reference for wiki content that must render **identically**
in the TermX Wiki (markdown-it via `@termx-health/markdown-parser`) and in
[`mdbook`](https://github.com/igorboss/mdbook) (VitePress, also markdown-it — but it compiles
markdown output as a **Vue template**, which is stricter than raw HTML). It is the counterpart to
mdbook's `docs/termx-wiki-compatibility.md` (the full feature matrix) and drives the one-time
content-convergence migration (`WikiContentNormalizer` + the `wiki:content-convergence`
changeset).

Nearly all TermX smart-text renders natively in mdbook. Only a handful of source constructs
diverge; this file lists them, the canonical both-compatible form, and how each is handled.

## Handling categories

- **Migrate** — the source is rewritten in place by the one-time convergence migration; the
  verbatim original is preserved in `wiki.page_content.bak_content`.
- **mdbook-side** — the gap is closed in the generator so both render the same; no content change.
- **Author-managed** — an intentional, page-specific construct; not touched automatically.

---

## Migrated rules

### R1 — Autolink-breaker spans → HTML comment
Wiki.js inserts a bare, attribute-less `<span>` to stop auto-linking inside a word
(e.g. `Draw.<span>io`, so "Draw.io" doesn't become a link). These are frequently **unclosed**,
which VitePress/Vue rejects ("Element is missing end tag").

- **Canonical form:** `Draw.<!-- -->io` — an empty HTML comment. It is invisible in both
  renderers and still splits the text so neither auto-links it.
- **Rewrite:** a bare, attribute-less opening `<span>` (and an empty `<span></span>` pair) →
  `<!-- -->`. Spans **with attributes** (`<span class="…">`, `style=…`) are meaningful, and a
  standalone closing `</span>` belongs to one — neither is touched.
- **Why not just delete** (mdbook's build-time fallback does): deletion rejoins `Draw.io`, which
  markdown-it's linkify may then turn into a link — a behavioral change. The comment preserves the
  original no-link intent in both.

### R2 — Stray fence languages → real language id
An unknown fenced-code language hard-fails the VitePress (Shiki) build. TermX content uses
` ```s ` for shell.

- **Rewrite:** ` ```s ` → ` ```sh `. The alias map is extensible; add any other stray ids the
  audit surfaces. (Mirrors mdbook's `FENCE_LANG_ALIAS` in `src/ingest/sanitize.mjs`.)
- Diagram fences (` ```drawio `, ` ```plantuml `, ` ```mermaid `) and real Shiki ids
  (`json`, `js`, `http`, `html`, `yaml`, `bash`, `plaintext`, …) are already fine — not touched.

---

## mdbook-side (no content change)

### `{.dense}` after a multimd table
`{.dense}` on its own line after a `^^`/`|||` multi-column table renders dense in the wiki, but
markdown-it-attrs can't attach the class to a multimd-table token, so mdbook can't apply it. Rather
than strip it from the source (which would drop the dense styling in the **wiki** too), the
generator closes the gap: mdbook attaches the class to the table element during staging so **both**
render dense. Content keeps `{.dense}`. (Tracks mdbook compatibility-doc §7.1.)

---

## Author-managed (not migrated)

- **Raw `<script>` blocks.** VitePress hoists `<script>` in markdown (executed); the wiki ignores
  it. In the current content these appear only inside ` ```html ` code fences (shown as examples),
  so they render as code in both. A genuinely raw, executable `<script>` is an intentional,
  page-specific choice — left to the author.
- **FHIR / XML examples** (`<system>`, `<valueCoding>`, …) live inside code fences and render as
  code in both — no action.
- **`{{def:}}` / `{{csc:}}` / `{{vsc:}}` and other `{{…}}`.** Both renderers handle these; mdbook
  makes them Vue-safe at render time (`v-pre`). No source change.

---

## Rollout

The convergence migration is **one-time**: it rewrites existing content and stores the original in
`bak_content`. It does not run on save — new content stays compatible by following this spec.
mdbook keeps its build-time `sanitize.mjs` as a belt-and-suspenders fallback for un-migrated or
third-party repos, so both migrated and unmigrated content render correctly.
