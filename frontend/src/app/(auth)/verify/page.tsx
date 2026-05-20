"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { CheckCircle, Loader2, XCircle } from "lucide-react";
import { verifyEmail } from "@/lib/api/auth";

function VerifyEmailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading"
  );

  useEffect(() => {
    if (!token) {
      setStatus("error");
      return;
    }

    let isMounted = true;

    verifyEmail(token)
      .then((res) => {
        if (!isMounted) return;
        if (res.success) {
          setStatus("success");
          setTimeout(() => {
            if (isMounted) router.push("/login?verified=true");
          }, 1200);
        } else {
          setStatus("error");
        }
      })
      .catch(() => {
        if (isMounted) setStatus("error");
      });

    return () => {
      isMounted = false;
    };
  }, [token, router]);

  return (
    <div className="flex flex-col items-center">
      {status === "loading" && (
        <>
          <Loader2 className="h-10 w-10 animate-spin" />
          <p className="mt-4 font-medium">Đang xác thực email của bạn...</p>
        </>
      )}

      {status === "success" && (
        <>
          <CheckCircle className="h-10 w-10 text-green-500" />
          <p className="mt-4 font-medium">Xác thực email thành công! Đang chuyển hướng...</p>
        </>
      )}

      {status === "error" && (
        <>
          <XCircle className="h-10 w-10 text-red-500" />
          <p className="mt-4 font-medium">Liên kết không hợp lệ hoặc đã hết hạn.</p>
          <button
            className="mt-4 underline"
            onClick={() => router.push("/login")}
          >
            Quay lại trang đăng nhập
          </button>
        </>
      )}
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen text-center p-6">
      <Suspense
        fallback={
          <div className="flex flex-col items-center">
            <Loader2 className="h-10 w-10 animate-spin" />
            <p className="mt-4 font-medium">Đang tải thông tin xác thực...</p>
          </div>
        }
      >
        <VerifyEmailContent />
      </Suspense>
    </div>
  );
}
