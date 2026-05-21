/** Either FILE or FOLDER hit; never both — backend tags the type. */
export type SearchHitType = "FILE" | "FOLDER";

/** Filter that restricts the search corpus. */
export type SearchType = "ALL" | "FILE" | "FOLDER";

/** Available sort keys for full-text search. `SCORE` is relevance. */
export type SearchSortBy = "SCORE" | "NAME" | "CREATED_AT";

export type SortDirection = "ASC" | "DESC";

/** Query params for GET /api/v1/search/suggest (search-as-you-type). */
export type SuggestParams = {
  /** 1–50 chars, trimmed. */
  q: string;
  /** Default: ALL. */
  type?: SearchType;
  /** 1–10. Default: 8. Values > 10 are clamped server-side. */
  size?: number;
};

/** Query params for GET /api/v1/search (fuzzy full-text). */
export type SearchParams = {
  /** Non-blank. */
  q: string;
  /** Default: ALL. */
  type?: SearchType;
  /** Repeatable on the wire. Ignored when type=FOLDER. */
  mimeType?: string[];
  /** ISO-8601 instant, inclusive lower bound on createdAt. */
  dateFrom?: string;
  /** ISO-8601 instant, inclusive upper bound on createdAt. */
  dateTo?: string;
  /** 1-based. Default: 1. */
  page?: number;
  /** 1–100. Default: 20. */
  size?: number;
  /** Default: SCORE. */
  sort?: SearchSortBy;
  /** Default: DESC. Ignored when sort=SCORE. */
  sortDir?: SortDirection;
};

/** Lightweight item returned by /search/suggest. */
export type SuggestHitItem = {
  id: string;
  type: SearchHitType;
  name: string;
  mimeType: string | null;
  folderId: string | null;
  folderPath: string | null;
};

export type SuggestResponse = {
  items: SuggestHitItem[];
  totalHits: number;
};

/**
 * HTML snippets with `<em>` around matched terms.
 * Backend HTML-escapes everything else, so these are safe to render.
 */
export type SearchHighlights = {
  originalName?: string[];
  content?: string[];
  name?: string[];
};

export type SearchHitItem = {
  id: string;
  type: SearchHitType;
  name: string;
  mimeType: string | null;
  folderId: string | null;
  folderPath: string | null;
  createdAt: string;
  score: number;
  highlights: SearchHighlights;
};

export type SearchAggregations = {
  /** Empty {} when type=FOLDER. */
  mimeTypeCounts: Record<string, number>;
};

export type SearchResponse = {
  items: SearchHitItem[];
  totalHits: number;
  page: number;
  size: number;
  aggregations: SearchAggregations;
};
