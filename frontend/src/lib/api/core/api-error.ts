import axios from "axios";

export type ApiErrorResponse<T = unknown> = {
  errors: T;
  success: false;
  code: number;
};

export class AppError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly validationErrors?: Record<string, string | string[]> | null,
    cause?: unknown
  ) {
    super(message, { cause });
    this.name = "AppError";

    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, AppError);
    }
  }
}

export const toAppError = (error: unknown): AppError => {
  if (error instanceof AppError) {
    return error;
  }

  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorResponse<unknown> | undefined;

    const status =
      typeof data?.code === "number" ? data.code : error.response?.status;

    if (data && data.success === false && "errors" in data) {
      const backendErrors = data.errors;

      if (typeof backendErrors === "string") {
        return new AppError(backendErrors, status, null, error);
      }

      if (Array.isArray(backendErrors)) {
        const message = backendErrors[0] || "Dữ liệu đầu vào không hợp lệ.";
        return new AppError(
          String(message),
          status,
          null,
          error
        );
      }

      if (typeof backendErrors === "object" && backendErrors !== null) {
        const entries = Object.entries(
          backendErrors as Record<string, unknown>
        );

        const firstValue = entries[0]?.[1];

        const message =
          typeof firstValue === "string"
            ? firstValue
            : Array.isArray(firstValue)
              ? firstValue[0] || "Dữ liệu đầu vào không hợp lệ."
              : "Dữ liệu đầu vào không hợp lệ.";

        const validationErrors: Record<string, string | string[]> = {};

        for (const [key, value] of entries) {
          if (typeof value === "string" || Array.isArray(value)) {
            validationErrors[key] = value;
          }
        }

        return new AppError(
          message,
          status,
          Object.keys(validationErrors).length ? validationErrors : null,
          error
        );
      }
    }

    return new AppError(
      error.message || "Đã xảy ra lỗi không xác định.",
      status,
      null,
      error
    );
  }

  if (error instanceof Error) {
    return new AppError(error.message, undefined, null, error);
  }

  return new AppError("Đã xảy ra lỗi không xác định.", undefined, null, error);
};
