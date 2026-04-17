export interface ApiResponse<T = undefined> {
  success: boolean;
  data?: T;
  message?: string;
  errors?: string | Record<string, string[]>;
  code?: number;
}

export type PageResponse<T> = {
  data: T[],
  pageSize: number,
  totalPages: number,
  totalElements: number,
  currentPage: number,
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: number;
  readonly errors?: string | Record<string, string[]>;

  constructor(
    message: string,
    status: number,
    code?: number,
    errors?: string | Record<string, string[]>
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}
