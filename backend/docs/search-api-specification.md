
# Search API Specification

Frontend integration guide for the file/folder search feature. This document is self-contained — you should not need to read any backend source or design docs to build against these endpoints.

Base path: `/api/v1`
Content type: `application/json`
Date format: ISO-8601 (`2026-05-21T08:30:00Z`)

---

## 1. Overview

The search feature exposes **two dedicated endpoints**, one per UX mode:

| Mode | Endpoint | When the FE calls it | What the user sees |
|---|---|---|---|
| **SAYT** (Search-As-You-Type) | `GET /api/v1/search/suggest` | Each keystroke, debounced ~500 ms | A small autocomplete dropdown (≤ 10 items) under the search bar |
| **Fuzzy full-text** | `GET /api/v1/search` | When the user presses **Enter** | A full results page with highlights, pagination, and a MIME-type filter sidebar |

The two endpoints are **intentionally separate** and must not be merged on the FE side:

- **Different response shapes.** Suggest returns the bare minimum the dropdown needs (id, name, mime, folder context). Search adds relevance score, highlight snippets, pagination metadata, and aggregation buckets.
- **Different latency/freshness budgets.** Suggest is on the per-keystroke hot path; search runs once per Enter.
- **Different validation rules.** Suggest caps `q` at 50 chars and `size` at 10. Search accepts longer queries, MIME/date filters, paging, and sort.
- **Different cache windows on the server.** Transparent to the FE — see §4.

Both endpoints search files **and** folders unless filtered with `type`.

---

## 2. Authentication & Authorization

### Authentication

All search endpoints require a valid session. Authentication is via an **HttpOnly cookie** named `access-token` set on login.

Frontend requirement:

```ts
fetch(url, { credentials: "include" })
```

The `Authorization: Bearer …` header is **not** honored. Do not read or send the token manually — the browser handles it.

Unauthenticated requests return `401 ACCESS_DENIED`.

### Authorization (visibility rules)

The backend reads `userId`, `tenantId`, and `role` from the JWT inside the cookie. **Never pass these as query parameters** — they are ignored if you do, and the server uses the JWT values regardless. This prevents a member from impersonating an admin's view via the URL.

Visibility per role:

| Role | What they see in results |
|---|---|
| `MEMBER` | Files they uploaded **plus** files/folders explicitly shared with them or their tenant. Nothing else, even within the same tenant. |
| `TENANT_ADMIN` | All files and folders in their tenant. |
| `SUPER_ADMIN` | All files and folders across all tenants. |

The same role filter is applied to both endpoints. A `MEMBER` calling `/search/suggest` and `/search` will see exactly the same scope; only the response shape differs.

---

## 3. Endpoints

### 3a. `GET /api/v1/search/suggest` — autocomplete dropdown

**Purpose:** Power the per-keystroke dropdown under the search bar. Returns a small list of file/folder name matches as the user types.

**When to call:** As the user types in the search input, after a debounce of ~500 ms. Cancel in-flight requests when a new keystroke arrives.

#### Query parameters

| Name | Type | Required | Default | Validation | Description |
|---|---|---|---|---|---|
| `q` | string | **Yes** | — | Trimmed length 1–50 chars | The prefix the user has typed. Whitespace is trimmed server-side. |
| `type` | enum | No | `ALL` | One of `ALL`, `FILE`, `FOLDER` | Restrict results to files only, folders only, or both. |
| `size` | integer | No | `8` | 1–10 (values > 10 are clamped to 10; ≤ 0 falls back to default) | Maximum number of items to return. |

#### Response — `200 OK`

```json
{
  "data": {
    "items": [
      {
        "id": "5f3c8a01-2c19-4b73-8b3f-9bd1e0d2f1a7",
        "type": "FILE",
        "name": "Q4 Report.pdf",
        "mimeType": "application/pdf",
        "folderId": "1a8d4d55-7a8a-4c1f-bbcb-2e3a1c0d2d4c",
        "folderPath": "Documents / Reports / Q4"
      },
      {
        "id": "6e7b9c14-3f29-4dab-92e1-7c5b6d8e9f00",
        "type": "FOLDER",
        "name": "Quarterly reports",
        "mimeType": null,
        "folderId": "1a8d4d55-7a8a-4c1f-bbcb-2e3a1c0d2d4c",
        "folderPath": "Documents / Reports"
      },
      {
        "id": "7c4e2d63-5b18-4a90-9d7e-4f1c2a8b0d11",
        "type": "FILE",
        "name": "Quarterly summary.docx",
        "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "folderId": null,
        "folderPath": null
      }
    ],
    "totalHits": 3
  },
  "success": true
}
```

#### Field semantics

- `items[].type` — either `"FILE"` or `"FOLDER"`. This drives the icon you render and where you navigate when the user clicks. The value is decided by the backend based on which collection the hit came from, so you can trust it (you do not need to inspect `mimeType` to tell files from folders).
- `items[].mimeType` — non-null for files only. Always `null` for folders.
- `items[].folderId` — the **containing folder's** id.
  - For a `FILE` hit: the folder the file lives in. `null` when the file is at the tenant root.
  - For a `FOLDER` hit: the **parent folder** of that folder. `null` when the folder is at the tenant root.
- `items[].folderPath` — a human-readable breadcrumb for `folderId`, joined with `" / "` (space, slash, space), e.g. `"Documents / Reports / Q4"`. `null` whenever `folderId` is `null` (the item lives at the root). Show this beneath the name in the dropdown so users can distinguish two files with identical names in different folders.
- `totalHits` — total number of matches across both files and folders (may be larger than `items.length` since the dropdown is capped).

#### Errors

| HTTP | `code` | When |
|---|---|---|
| `400` | `1` | `q` missing, empty after trim, or longer than 50 chars; `size` is not a valid integer. |
| `401` | `2` | Missing or invalid session cookie. |
| `403` | `3` | Session is valid but the role is not allowed (currently never returned for this endpoint, reserved). |

---

### 3b. `GET /api/v1/search` — fuzzy full-text search

**Purpose:** Run a relevance-ranked search across file names, folder names, and the extracted full-text content of files. Returns paginated hits with highlights and a MIME-type aggregation suitable for a filter sidebar.

**When to call:** When the user presses **Enter** in the search bar, or when they change a filter / page / sort control on the results page.

The matching is fuzzy by default — `q="reprot"` will still match documents containing "report". Exact matches always rank higher than typo-tolerant matches, so behavior degrades gracefully to "regular search" when the query is spelled correctly.

#### Query parameters

| Name | Type | Required | Default | Validation | Description |
|---|---|---|---|---|---|
| `q` | string | **Yes** | — | Non-blank | The user's query string. |
| `type` | enum | No | `ALL` | One of `ALL`, `FILE`, `FOLDER` | Restrict to files, folders, or both. |
| `mimeType` | string (repeatable) | No | — | Any string; unknown values match nothing | Filter to specific MIME types. Repeat the param to OR multiple values: `?mimeType=application/pdf&mimeType=text/plain`. Ignored when `type=FOLDER`. |
| `dateFrom` | ISO-8601 instant | No | — | Valid ISO timestamp | Lower bound (inclusive) on `createdAt`. |
| `dateTo` | ISO-8601 instant | No | — | Valid ISO timestamp | Upper bound (inclusive) on `createdAt`. |
| `page` | integer | No | `1` | ≥ 1 | 1-based page number. |
| `size` | integer | No | `20` | 1–100 | Page size. |
| `sort` | enum | No | `SCORE` | One of `SCORE`, `NAME`, `CREATED_AT` | Sort key. `SCORE` is relevance. |
| `sortDir` | enum | No | `DESC` | One of `ASC`, `DESC` | Sort direction. Ignored when `sort=SCORE` (always descending). |

#### Response — `200 OK`

```json
{
  "data": {
    "items": [
      {
        "id": "5f3c8a01-2c19-4b73-8b3f-9bd1e0d2f1a7",
        "type": "FILE",
        "name": "Q4 Report.pdf",
        "mimeType": "application/pdf",
        "folderId": "1a8d4d55-7a8a-4c1f-bbcb-2e3a1c0d2d4c",
        "folderPath": "Documents / Reports / Q4",
        "createdAt": "2026-04-12T09:31:02Z",
        "score": 12.34,
        "highlights": {
          "originalName": ["Q4 <em>Report</em>.pdf"],
          "content": [
            "…the quarterly <em>report</em> for fiscal year 2026 covers…",
            "…attached <em>report</em> includes regional breakdowns…"
          ]
        }
      },
      {
        "id": "7c4e2d63-5b18-4a90-9d7e-4f1c2a8b0d11",
        "type": "FILE",
        "name": "Quarterly summary.docx",
        "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "folderId": null,
        "folderPath": null,
        "createdAt": "2026-03-29T15:02:44Z",
        "score": 8.71,
        "highlights": {
          "originalName": ["<em>Quarterly</em> summary.docx"]
        }
      },
      {
        "id": "6e7b9c14-3f29-4dab-92e1-7c5b6d8e9f00",
        "type": "FOLDER",
        "name": "Quarterly reports",
        "mimeType": null,
        "folderId": "1a8d4d55-7a8a-4c1f-bbcb-2e3a1c0d2d4c",
        "folderPath": "Documents / Reports",
        "createdAt": "2026-01-08T11:14:00Z",
        "score": 5.42,
        "highlights": {
          "name": ["<em>Quarterly</em> reports"]
        }
      }
    ],
    "totalHits": 47,
    "page": 1,
    "size": 20,
    "aggregations": {
      "mimeTypeCounts": {
        "application/pdf": 30,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document": 12,
        "text/plain": 5
      }
    }
  },
  "success": true
}
```

#### Field semantics

- `items[].type` — `"FILE"` or `"FOLDER"`. Same semantics as the suggest endpoint.
- `items[].mimeType` — file only; `null` for folders.
- `items[].folderId` and `items[].folderPath` — same semantics as the suggest endpoint (containing folder for files, parent folder for folders; `folderPath` is `null` at the root).
- `items[].createdAt` — ISO-8601 timestamp, useful for sort/display.
- `items[].score` — relevance score from the search engine. Use only for relative ordering or debug display; it is **not** a normalized 0–1 value and absolute magnitudes are not comparable across queries.
- `items[].highlights` — map from field name to a list of snippets. Matched terms are wrapped in `<em>` tags. Render snippets with `dangerouslySetInnerHTML` (or your framework's equivalent) **after** confirming the value came from this API — the backend only ever inserts `<em>…</em>` and HTML-escapes everything else.
  - Possible keys for files: `originalName`, `content`.
  - Possible keys for folders: `name`.
  - A field is omitted from `highlights` entirely if there was no match against it. Always treat `highlights` and each of its keys as optional.
- `totalHits` — total matches across the result set (not just the current page). Use this for the pager.
- `page`, `size` — echo of the requested page/size, so you can render a stable pager without tracking your own request state.
- `aggregations.mimeTypeCounts` — bucketed counts of distinct MIME types in the current result set. Use this to populate a MIME filter sidebar.
  - Populated when `type=FILE` or `type=ALL`.
  - **Always `{}` (empty object, never `null` or missing) when `type=FOLDER`** — folders have no MIME type to aggregate.
  - When `type=ALL`, counts come from the file portion of the result set only; folder hits are not included in any bucket.

#### Errors

| HTTP | `code` | When |
|---|---|---|
| `400` | `1` | `q` missing or blank; non-parseable `dateFrom`/`dateTo`; invalid enum values for `type`/`sort`/`sortDir`; non-numeric `page`/`size`. |
| `401` | `2` | Missing or invalid session cookie. |
| `403` | `3` | Session is valid but the role is not allowed (currently never returned for this endpoint, reserved). |

---

## 4. Frontend integration guide

### 4.1 SAYT flow

```
focus search bar
  │
  ▼
user types  ───►  debounce 500 ms  ───►  GET /api/v1/search/suggest?q=<prefix>&size=8
                                                  │
                                                  ▼
                                         render dropdown (≤ 10 items)
                                                  │
                                ┌─────────────────┴─────────────────┐
                                ▼                                   ▼
                  user clicks an item                   user presses Enter
                                │                                   │
                                ▼                                   ▼
              navigate using (id, type)            GET /api/v1/search?q=…&page=1
              FILE   → file preview / detail               (see 4.2)
              FOLDER → folder browse view
```

Implementation tips:

- **Debounce**, don't throttle. 500 ms is the sweet spot used by the spec; 300–500 ms is acceptable.
- **Cancel in-flight requests** on each new keystroke (use `AbortController`). The dropdown should never display results for a stale prefix.
- **Empty query** — do not call the endpoint when the trimmed query is empty; just clear the dropdown.
- **Click target** — use `(item.id, item.type)` to decide where to navigate. Don't infer type from `mimeType`.
- **Display** — show the name on the primary line and `folderPath` on a smaller secondary line. Skip the secondary line when `folderPath` is `null`.

### 4.2 Full-text flow

```
user presses Enter / changes filter / changes page / changes sort
  │
  ▼
GET /api/v1/search?q=…&type=…&mimeType=…&dateFrom=…&dateTo=…&page=…&size=…&sort=…&sortDir=…
  │
  ▼
render
  • result list (items + highlights)
  • pager (totalHits, page, size)
  • MIME filter sidebar (aggregations.mimeTypeCounts)
```

Implementation tips:

- **Highlights**: prefer `highlights.originalName` (or `highlights.name` for folders) for the title, falling back to `name` if no highlight exists. For content matches, render `highlights.content` snippets beneath the title.
- **Sanitization**: highlights only contain `<em>…</em>` tags; all other content is HTML-escaped by the server. You can render them as HTML, but never inject other user-supplied strings the same way.
- **Pagination**: total pages = `Math.ceil(totalHits / size)`. The server echoes `page` and `size` in the response, so a stale request landing late will not desync your pager.
- **MIME filter sidebar**: build it from `aggregations.mimeTypeCounts`. When the user toggles a MIME on, add it to the `mimeType` query param (repeatable). When `type=FOLDER`, the map is empty — hide the sidebar in that mode.
- **Empty state**: `totalHits === 0` means no matches. Show an empty state and surface the active filters so the user can clear them.
- **Date filters**: send instants in ISO-8601, e.g. `2026-04-01T00:00:00Z`. Both bounds are inclusive.

### 4.3 Caching behavior (transparent)

The backend caches both endpoints (suggest ~60 seconds, search ~5 minutes), keyed by the requesting user and their query. The cache is invalidated automatically whenever a file/folder visible to that user changes — uploads, renames, moves, deletes.

**Frontend does not need any cache logic of its own.** Just call the endpoint normally; a hit will be fast and a miss will run a fresh query. There is no `If-None-Match`, no `ETag`, no stale-while-revalidate contract.

If your FE has its own client-side cache, keep its TTL short (≤ 30 s for suggest, ≤ 1 min for search) to avoid amplifying staleness; the backend cache already absorbs most repeated requests.

### 4.4 Error handling

All errors return a uniform envelope:

```json
{
  "errors": "Invalid query parameter",
  "success": false,
  "code": 1
}
```

- `success` is always `false` for errors.
- `code` is a project-specific numeric code (not the HTTP status). The codes relevant to search are listed under each endpoint's "Errors" table; the most common are `1` (invalid payload), `2` (unauthenticated), `3` (forbidden).
- `errors` is either a string (most cases) or, for `MethodArgumentNotValid` (`400` with `code=1`), a map of `fieldName → [messages]`. Treat `errors` as `string | Record<string, string[]>` and branch on type.

Recommended handling:

| `code` | UX |
|---|---|
| `1` | Show a toast / inline message based on the error string; reset bad inputs. |
| `2` | Bounce to login (the cookie is missing or expired). |
| `3` | Show a "you don't have access" toast; do not retry. |
| Network / 5xx | Show a transient error, allow retry. |

---

## 5. Data model reference

Field-by-field summary of the response items.

| Field | Type | In SAYT (`/suggest`) | In Full-text (`/search`) | Description |
|---|---|---|---|---|
| `id` | `string (uuid)` | ✓ | ✓ | Stable identifier of the file or folder. Use this for navigation and de-duplication. |
| `type` | `"FILE" \| "FOLDER"` | ✓ | ✓ | Whether this hit is a file or a folder. Drives icon, navigation target, and which other fields are populated. |
| `name` | `string` | ✓ | ✓ | Display name. For files this is the original filename; for folders the folder name. |
| `mimeType` | `string \| null` | ✓ | ✓ | MIME type for files (e.g. `"application/pdf"`); always `null` for folders. |
| `folderId` | `string (uuid) \| null` | ✓ | ✓ | Containing folder for files; parent folder for folders. `null` at the tenant root. |
| `folderPath` | `string \| null` | ✓ | ✓ | Human-readable breadcrumb for `folderId`, e.g. `"Documents / Reports / Q4"`. `null` when `folderId` is `null`. |
| `createdAt` | `string (ISO-8601)` | ✗ | ✓ | When the file/folder was created. |
| `score` | `number` | ✗ | ✓ | Relevance score. Relative only — do not compare across queries. |
| `highlights` | `object` | ✗ | ✓ | Per-field array of HTML snippets with `<em>` around matched terms. Possible keys: `originalName`, `content` (files); `name` (folders). Field omitted when no match. |

Top-level response (data envelope):

| Field | Type | In SAYT | In Full-text | Description |
|---|---|---|---|---|
| `items` | `array` | ✓ | ✓ | The hits. |
| `totalHits` | `number` | ✓ | ✓ | Total matches across the result set. |
| `page` | `number` | ✗ | ✓ | Echo of the requested page. |
| `size` | `number` | ✗ | ✓ | Echo of the requested page size. |
| `aggregations.mimeTypeCounts` | `Record<string, number>` | ✗ | ✓ | MIME-type bucket counts. Empty `{}` when `type=FOLDER`. |

---

## 6. TypeScript type definitions

Drop these into a shared `types/search.ts` (or equivalent). They model the response **inside** the `data` envelope returned by the API — unwrap `ApiResponse<T>` first.

```ts
// ── Shared envelopes ────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  data: T;
  success: true;
}

export interface ApiError {
  errors: string | Record<string, string[]>;
  success: false;
  /** Project-specific error code (not the HTTP status). */
  code: number;
}

// ── Enums ───────────────────────────────────────────────────────────────────

export type SearchType = "ALL" | "FILE" | "FOLDER";
export type SearchSortBy = "SCORE" | "NAME" | "CREATED_AT";
export type SortDirection = "ASC" | "DESC";
export type SearchHitType = "FILE" | "FOLDER";

// ── Request params ──────────────────────────────────────────────────────────

export interface SuggestParams {
  /** 1–50 chars, trimmed. */
  q: string;
  /** Default: ALL. */
  type?: SearchType;
  /** 1–10. Default: 8. Values > 10 are clamped. */
  size?: number;
}

export interface SearchParams {
  /** Non-blank. */
  q: string;
  /** Default: ALL. */
  type?: SearchType;
  /** Repeatable on the wire. Ignored when type=FOLDER. */
  mimeType?: string[];
  /** ISO-8601 instant, e.g. "2026-04-01T00:00:00Z". Inclusive lower bound on createdAt. */
  dateFrom?: string;
  /** ISO-8601 instant. Inclusive upper bound on createdAt. */
  dateTo?: string;
  /** 1-based. Default: 1. */
  page?: number;
  /** 1–100. Default: 20. */
  size?: number;
  /** Default: SCORE. */
  sort?: SearchSortBy;
  /** Default: DESC. Ignored when sort=SCORE. */
  sortDir?: SortDirection;
}

// ── Response items ──────────────────────────────────────────────────────────

export interface SuggestHitItem {
  id: string;
  type: SearchHitType;
  name: string;
  mimeType: string | null;
  folderId: string | null;
  folderPath: string | null;
}

export interface SuggestResponse {
  items: SuggestHitItem[];
  totalHits: number;
}

export interface SearchHighlights {
  /** Files only. */
  originalName?: string[];
  /** Files only. */
  content?: string[];
  /** Folders only. */
  name?: string[];
}

export interface SearchHitItem {
  id: string;
  type: SearchHitType;
  name: string;
  mimeType: string | null;
  folderId: string | null;
  folderPath: string | null;
  createdAt: string;
  score: number;
  /** Map of field-name → list of HTML snippets with <em> markers. */
  highlights: SearchHighlights;
}

export interface SearchAggregations {
  /** Empty {} when type=FOLDER. */
  mimeTypeCounts: Record<string, number>;
}

export interface SearchResponse {
  items: SearchHitItem[];
  totalHits: number;
  page: number;
  size: number;
  aggregations: SearchAggregations;
}
```

---

## 7. Example calls (fetch)

### 7.1 Suggest (autocomplete dropdown)

```ts
import type {
  ApiResponse,
  ApiError,
  SuggestParams,
  SuggestResponse,
} from "./types/search";

export async function fetchSuggest(
  params: SuggestParams,
  signal?: AbortSignal
): Promise<SuggestResponse> {
  const query = new URLSearchParams();
  query.set("q", params.q);
  if (params.type) query.set("type", params.type);
  if (params.size != null) query.set("size", String(params.size));

  const res = await fetch(`/api/v1/search/suggest?${query.toString()}`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  const body = (await res.json()) as ApiResponse<SuggestResponse> | ApiError;

  if (!res.ok || body.success === false) {
    const err = body as ApiError;
    throw Object.assign(new Error("Suggest request failed"), {
      status: res.status,
      code: err.code,
      errors: err.errors,
    });
  }

  return body.data;
}

// Usage inside a debounced effect:
//
//   const ctrl = new AbortController();
//   fetchSuggest({ q: "report", size: 8 }, ctrl.signal)
//     .then(view => renderDropdown(view.items))
//     .catch(e => { if (e.name !== "AbortError") showError(e); });
//
//   // on next keystroke:
//   ctrl.abort();
```

### 7.2 Full-text search (Enter key + filters + paging)

```ts
import type {
  ApiResponse,
  ApiError,
  SearchParams,
  SearchResponse,
} from "./types/search";

export async function fetchSearch(
  params: SearchParams,
  signal?: AbortSignal
): Promise<SearchResponse> {
  const query = new URLSearchParams();
  query.set("q", params.q);
  if (params.type) query.set("type", params.type);
  if (params.mimeType) {
    for (const m of params.mimeType) query.append("mimeType", m);
  }
  if (params.dateFrom) query.set("dateFrom", params.dateFrom);
  if (params.dateTo) query.set("dateTo", params.dateTo);
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);
  if (params.sortDir) query.set("sortDir", params.sortDir);

  const res = await fetch(`/api/v1/search?${query.toString()}`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  const body = (await res.json()) as ApiResponse<SearchResponse> | ApiError;

  if (!res.ok || body.success === false) {
    const err = body as ApiError;
    throw Object.assign(new Error("Search request failed"), {
      status: res.status,
      code: err.code,
      errors: err.errors,
    });
  }

  return body.data;
}

// Usage: search for "report" filtered to PDFs and Word docs, last 30 days,
// page 2 sorted by relevance.
//
//   const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
//
//   fetchSearch({
//     q: "report",
//     type: "FILE",
//     mimeType: [
//       "application/pdf",
//       "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
//     ],
//     dateFrom: since,
//     page: 2,
//     size: 20,
//     sort: "SCORE",
//   }).then(view => {
//     renderResults(view.items);
//     renderPager(view.page, view.size, view.totalHits);
//     renderMimeSidebar(view.aggregations.mimeTypeCounts);
//   });
```
