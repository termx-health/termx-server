# FHIR ETag + `If-None-Match` Revalidation

**Feature:** Weak `ETag` emission on every FHIR read response (`GET`/`HEAD` on `/fhir/*` with status `200`) + short-circuit to `304 Not Modified` when the client supplies a matching `If-None-Match` header.
**Spans:** [`termx-core`](../../termx-core) (the kefhir filter pair `FhirEtagFilter`).
**Introduced:** [termx-server#163](https://github.com/termx-health/termx-server/pull/163) — landed 2026-05-24.
**Companion:** [e-medlab/terminology-explorer#27](https://github.com/e-medlab/terminology-explorer/pull/27) — the same contract at the federation-router layer that sits in front of TermX in the Helex deployment.

---

## 1. Motivation

Before this feature TermX FHIR reads emitted

```
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
```

on every response. Every HTTP cache in the path — reverse proxy, browser, internal forwarder — was forbidden from storing the body. Clients refetched on every request, paying the resource-build cost even when nothing had changed since the previous fetch.

Concrete cost observed in the Helex deployment of TermX on 2026-05-24: federated `/fhir/CodeSystem` reads against a cold catalogue cache took **30-60 s for a single request, and 4-8 min when the UI fanned out five parallel queries** (catalogue listing + facet dropdowns). The federation handler did not internally single-flight identical concurrent requests, so the parallel walks compounded their cost while fighting for GC. Access-log fragment:

```
14:46:05.473  GET /fhir/CodeSystem  Duration: 470127ms
14:46:05.525  GET /fhir/CodeSystem  Duration: 426342ms
14:46:05.528  GET /fhir/CodeSystem  Duration: 348231ms
14:46:05.531  GET /fhir/CodeSystem  Duration: 440508ms
14:46:05.540  GET /fhir/CodeSystem  Duration: 259673ms
```

A shared HTTP cache in front of TermX is the natural mitigation, but `no-store` forbade it. This feature replaces `no-store` with a revalidating shape — caches may store but **must revalidate before serving** — and gives them the cheap validator (`ETag`) they need to do so.

## 2. What changed

`org.termx.core.fhir.etag.FhirEtagFilter` in `termx-core` implements kefhir's `KefhirRequestFilter` + `KefhirResponseFilter` interfaces in one class. Filter order = `100` (after kefhir's own filters in the 1-50 band).

On `GET`/`HEAD` requests with status `200` and a `Resource` body:

1. The body is serialised through the existing `ResourceFormatService` in the format kefhir's `RuleThemAllFhirController` will use for the wire response (picked from the request's `Accept` list).
2. A weak ETag of the form `W/"<crc32-hex>-<byte-length>"` is computed over the resulting bytes.
3. `Cache-Control` is rewritten to `public, max-age=0, must-revalidate`; `Pragma` and `Expires` are removed from the response header map.
4. If the request carried `If-None-Match` and any element matches (exact, wildcard `*`, or comma-separated list per RFC 9110 §13.1.2), the response is mutated to `status = 304`, `body = null`. The ETag is echoed so the client/cache can refresh its TTL on the stored entry. Otherwise the ETag is added to the response headers and the normal body flow continues.

Everything else (mutating methods, non-200 statuses, non-`Resource` bodies, paths outside `/fhir/*`) is left untouched.

## 3. Why weak ETag

FHIR resource serialisation isn't byte-deterministic across runs — element ordering inside arrays, JSON key ordering, pretty-printing whitespace may all vary between equivalent representations. A **strong** validator would produce false misses on each variation, defeating the cache. **Weak** validators (RFC 9110 §8.8.1) explicitly allow matching across such semantically-equivalent representations.

CRC32 is HW-accelerated on modern JVMs; the length suffix makes accidental collisions vanishingly rare. ETags are not security tokens — cryptographic strength is not required.

## 4. Scope and intentional non-scope

| Aspect | Scope |
|---|---|
| Methods | `GET` / `HEAD` only. `POST` / `PUT` / `PATCH` / `DELETE` bypass the filter unchanged — they're not cacheable. |
| Status | `200 OK` only. Errors (4xx / 5xx) get no ETag — caches must not memoize a transient failure. |
| Body types | `Resource` only. Non-`Resource` bodies (raw `String`, framework error envelopes, …) are skipped. |
| Paths | Any path kefhir's `RuleThemAllFhirController` dispatches. The filter sees only `KefhirRequest` / `KefhirResponse`, which are FHIR by construction. |
| Content negotiation | Filter uses the same format the controller will use for the wire response. JSON and XML both supported. |

## 5. Live behaviour

The filter pair was verified end-to-end against a deployed instance:

```
$ URL='https://<tx-host>/fhir/CodeSystem?_count=20&_summary=true'

$ curl -sI -H 'Accept: application/fhir+json' "$URL" | grep -iE 'http/|etag|cache-control'
HTTP/2 200
cache-control: public, max-age=0, must-revalidate
etag: W/"87f0d2e6-1072"

$ curl -sI -H 'Accept: application/fhir+json' \
       -H 'If-None-Match: W/"87f0d2e6-1072"' "$URL" | grep -iE 'http/|etag|cache-control'
HTTP/2 304
cache-control: public, max-age=0, must-revalidate
etag: W/"87f0d2e6-1072"
```

- First request: `200 OK` with the `ETag` and the rewritten `Cache-Control`. No `Pragma` / `Expires` (cleared by the filter).
- Second request with `If-None-Match` matching the ETag: `304 Not Modified` with empty body. Same ETag echoed so the client/cache can refresh its stored entry's TTL.

## 6. Tests

`FhirEtagFilterSpec` (11 cases under `termx-core/src/test/groovy/org/termx/core/fhir/etag/`):

- happy path 200 → ETag emitted
- `If-None-Match` match → 304 + null body
- wildcard `If-None-Match: *` always 304
- comma-separated `If-None-Match` list matches any element
- different bodies → different ETags
- identical bodies → identical ETags
- POST passes through untouched (no `ResourceFormatService` call)
- non-200 response passes through (no ETag)
- non-`Resource` body passes through
- `Pragma` / `Expires` cleared on success path
- no-matching-presenter for client `Accept` → filter no-ops, lets kefhir's own content-type negotiation fail normally

`termx-core` full test suite: **57 / 57 passing**.

## 7. Build dependencies

Two test-only additions in `termx-core/build.gradle.kts`:

- `testImplementation("com.kodality.kefhir:fhir-rest:${kefhirVersion}")` — `fhir-rest` is `compileOnly` in production (provided by `termx-app` at runtime); the spec needs `KefhirRequest` / `KefhirResponse` on the test classpath.
- `testRuntimeOnly("org.objenesis:objenesis:3.4")` — Spock needs Objenesis to mock the concrete `ResourceFormatService` (no no-arg constructor).

Production runtime dependencies are unchanged.

## 8. Operator nginx-config recipe

`termx-server` doesn't bundle an HTTP-cache layer — operators put their own reverse proxy in front. Below is the nginx config that takes full advantage of the new ETag. Each `cache*` directive is annotated with what it does and why.

Two parts: the cache **zone** (declared once at `http` scope) and the **location block** (per FHIR read surface).

### Zone (top of the `nginx.conf` or a drop-in inside `http {}`)

```nginx
proxy_cache_path /var/cache/nginx/termx
                 levels=1:2
                 keys_zone=termx_fhir:10m
                 max_size=200m
                 inactive=60m
                 use_temp_path=off;
```

| Directive | Meaning |
|---|---|
| `proxy_cache_path /var/cache/nginx/termx` | On-disk root for stored response bodies. nginx must have write access. |
| `levels=1:2` | Two-level hash-directory layout under the root — keeps any one directory from holding too many files. Standard recipe. |
| `keys_zone=termx_fhir:10m` | Named shared-memory zone holding cache metadata (keys, expiry timestamps). `10m` ≈ 80 000 cached responses. |
| `max_size=200m` | Hard cap on disk usage. FHIR resources are small (CodeSystem catalogues kB, `$expand` pages tens of kB); 200 MB holds thousands of entries. |
| `inactive=60m` | Entries not requested for 60 minutes are evicted, even if `proxy_cache_valid` would otherwise keep them. Cleans up stale keys without operator intervention. |
| `use_temp_path=off` | Write directly into the cache directory instead of staging through a temp dir — saves one rename per cache write. Required when the cache dir is on the same filesystem as the temp path anyway. |

### Location block (inside `server { }`, fronting the FHIR read paths)

```nginx
location /fhir/ {
    proxy_pass http://<termx-backend-host>:<port>;
    proxy_http_version 1.1;

    proxy_cache             termx_fhir;
    proxy_cache_key         "$scheme$request_method$host$request_uri";
    proxy_cache_methods     GET HEAD;
    proxy_cache_lock        on;
    proxy_cache_lock_timeout 90s;
    proxy_cache_lock_age     90s;
    proxy_cache_valid       200 1h;
    proxy_cache_valid       404 30s;
    proxy_cache_revalidate  on;
    proxy_cache_use_stale   updating error timeout
                            http_500 http_502 http_503 http_504;
    proxy_cache_background_update on;
    proxy_no_cache          $http_authorization;
    proxy_cache_bypass      $http_authorization $http_x_cache_bypass $arg_nocache;
    proxy_ignore_headers    Cache-Control Expires Set-Cookie X-Accel-Expires Vary;
    add_header              X-Cache-Status $upstream_cache_status always;

    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

| Directive | Meaning |
|---|---|
| `proxy_cache termx_fhir` | Bind this location to the cache zone declared above. |
| `proxy_cache_key "$scheme$request_method$host$request_uri"` | One cache entry per (https/http, GET/HEAD, host, full URI including query string). Two clients hitting the same URL share the same entry. |
| `proxy_cache_methods GET HEAD` | Only safe verbs are stored. `POST` / `PUT` / `PATCH` / `DELETE` bypass the cache entirely. |
| `proxy_cache_lock on` | **Single-flight.** When N concurrent identical requests miss the cache, nginx forwards exactly one upstream and parks the other N-1 on the response. This is the thundering-herd guard — without it, every concurrent miss multiplies upstream cost. |
| `proxy_cache_lock_timeout 90s` | If the leader hasn't produced a response in 90 s, waiters are allowed to forward their own requests upstream. Safety valve for a slow upstream. |
| `proxy_cache_lock_age 90s` | If the leader holds the lock for 90 s without producing data, treat its slot as abandoned and let the next waiter try. Distinct from `lock_timeout`, which times out waiters. |
| `proxy_cache_valid 200 1h` | Successful responses live in the cache for an hour. Safe to keep long because `proxy_cache_revalidate` makes the refresh cheap. |
| `proxy_cache_valid 404 30s` | Negative caching: store 404s briefly so a one-time miss doesn't pin the upstream for an hour. 30 s lets transient deletions resolve quickly. |
| `proxy_cache_revalidate on` | **The ETag pay-off.** When a cached entry expires, nginx sends the stored `ETag` upstream via `If-None-Match`. `termx-server`'s `FhirEtagFilter` replies `304 Not Modified` if the body is unchanged — nginx refreshes the entry's TTL without re-downloading. The 304 path is a few-ms header round-trip instead of a full resource rebuild. |
| `proxy_cache_use_stale updating error timeout http_500 http_502 http_503 http_504` | While a background refresh is in flight (`updating`) or the upstream is unhealthy (error / timeout / 5xx), serve the last-known-good cached body instead of propagating the error. Users see the stale-but-correct version instead of a failure. |
| `proxy_cache_background_update on` | After TTL expires, the first user request triggers an *async* refresh and gets the stale body **immediately**. Subsequent users see the fresh body once the background refresh lands. Combined with `use_stale updating`, this hides the upstream latency from interactive requests. |
| `proxy_no_cache $http_authorization` | Don't *store* responses to requests carrying `Authorization`. Protects against accidentally sharing a privileged response with anonymous clients via the cache. |
| `proxy_cache_bypass $http_authorization $http_x_cache_bypass $arg_nocache` | Don't *serve* a cached response when the request carries `Authorization` (paired with `proxy_no_cache`), OR an `X-Cache-Bypass` header (operator-initiated single-key refresh), OR a `?nocache=…` query parameter (alternative bypass syntax). The cache is still updated with whatever the upstream returns. |
| `proxy_ignore_headers Cache-Control Expires Set-Cookie X-Accel-Expires Vary` | Override upstream cache directives. `termx-server` may still emit `Cache-Control: no-store` on some paths in older builds; without this, nginx respects that directive and refuses to store the body. Safe to ignore here because FHIR catalogue reads are intentionally cacheable. |
| `add_header X-Cache-Status $upstream_cache_status always` | Emits `X-Cache-Status: HIT \| MISS \| STALE \| UPDATING \| BYPASS \| EXPIRED \| REVALIDATED` so operators / monitoring / `curl -I` can see cache behaviour at a glance. `always` ensures the header is present even on error responses. |

### Verification after deploy

```
$ curl -sI 'https://<host>/fhir/CodeSystem?_count=20&_summary=true' | grep -iE 'http/|etag|x-cache'
HTTP/2 200
etag: W/"…"
x-cache-status: MISS         # first request

$ curl -sI 'https://<host>/fhir/CodeSystem?_count=20&_summary=true' | grep -iE 'http/|etag|x-cache'
HTTP/2 200
etag: W/"…"
x-cache-status: HIT          # second request — served from nginx, no upstream call
```

## 9. References

- RFC 9110 §§ 8.8.1 (weak validators), 13.1.2 (`If-None-Match`).
- RFC 9111 § 4.3 (cache revalidation).
- [`StructureMapTransformOperationHack`](../../modeler/src/main/java/org/termx/modeler/fhir/StructureMapTransformOperationHack.java) — the existing `KefhirRequestFilter` + `KefhirResponseFilter` pair the new filter is modelled after.
