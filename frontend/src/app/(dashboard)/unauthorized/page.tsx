import Link from "next/link";

export default function UnauthorizedPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
      <h1 className="text-3xl font-bold">403 — Không có quyền truy cập</h1>
      <p className="text-muted-foreground">
        Bạn không có quyền truy cập vào trang này.
      </p>
      <Link
        href="/files"
        className="text-primary underline underline-offset-4 hover:text-primary/80"
      >
        Quay lại trang Tệp tin
      </Link>
    </div>
  );
}
