"use client";

import { useRouter } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { CheckCircle, Loader2, XCircle } from "lucide-react";
import { useSecureToken } from "@/hooks/use-secure-token";

function VerifyEmailContent() {
  const router = useRouter();
  const { token, isLoading: tokenLoading, error: tokenError } = useSecureToken({
    type: "verify",
  });
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading"
  );

  useEffect(() => {
    if (tokenLoading) return;

    if (tokenError || !token) {
      setStatus("error");
      return;
    }

    let isMounted = true;
    setStatus("success");
    const redirectTimer = setTimeout(() => {
      if (!isMounted) return;
      router.push("/login?verified=true");
    }, 1200);

    return () => {
      isMounted = false;
      clearTimeout(redirectTimer);
    };
  }, [token, tokenLoading, tokenError, router]);

  return (
    <div className="flex flex-col items-center">
      {(status === "loading" || tokenLoading) && (
        <>
          <Loader2 className="h-10 w-10 animate-spin" />
          <p className="mt-4 font-medium">Verifying your email...</p>
        </>
      )}

      {status === "success" && (
        <>
          <CheckCircle className="h-10 w-10 text-green-500" />
          <p className="mt-4 font-medium">Email verified! Redirecting...</p>
        </>
      )}

      {status === "error" && !tokenLoading && (
        <>
          <XCircle className="h-10 w-10 text-red-500" />
          <p className="mt-4 font-medium">Invalid or expired link.</p>
          <button
            className="mt-4 underline"
            onClick={() => router.push("/login")}
          >
            Return to sign in
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
            <p className="mt-4 font-medium">Loading verification...</p>
          </div>
        }
      >
        <VerifyEmailContent />
      </Suspense>
    </div>
  );
}