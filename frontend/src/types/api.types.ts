export interface ApiResponse<T = undefined> {
  success: boolean;
  data?: T;
  message?: string;
}

export type PageResponse<T> = {
  data: T[],
  pageSize: number,
  totalPages: number,
  totalElements: number,
  currentPage: number,
}
