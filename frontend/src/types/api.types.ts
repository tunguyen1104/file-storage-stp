export interface ApiResponse<T> {
  data?: T;
  message?: string;
  errorCode?: number;
  errors?: Record<string, string>;
  status: "SUCCESS" | "ERROR";
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errorCode?: number;
  readonly errors?: Record<string, string>;

  constructor(
    message: string,
    status: number,
    errorCode?: number,
    errors?: Record<string, string>
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errorCode = errorCode;
    this.errors = errors;
  }
}
