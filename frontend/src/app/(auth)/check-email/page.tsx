"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Loader2, Mail } from "lucide-react";
import { Suspense, useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";
import { maskEmail } from "@/lib/auth";

const RESEND_COOLDOWN_SECONDS = 60;

function CheckEmailForm() {
  const searchParams = useSearchParams();
  const email = searchParams.get("email");
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  // #6: Cooldown timer to prevent resend spam
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((c) => (c <= 1 ? 0 : c - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const handleResend = async () => {
    if (!email || cooldown > 0) return;
    setLoading(true);

    try {
      await apiClient.post("/api/auth/resend-verification", { email });
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch {
      // Silent fail — user sees no change
    } finally {
      setLoading(false);
    }
  };

  // #12: Mask email to prevent phishing via crafted URLs
  const displayEmail = email ? maskEmail(email) : "your email";

  return (
    <Card className="w-full max-w-md shadow-lg">
      <CardHeader className="text-center">
        <div className="flex justify-center mb-2">
          <Mail className="w-10 h-10" />
        </div>
        <CardTitle className="text-2xl font-bold">Check your email</CardTitle>
        <p className="text-sm text-muted-foreground mt-1">
          We&apos;ve sent a verification link to:
          <br />
          <span className="font-medium text-foreground">{displayEmail}</span>
        </p>
      </CardHeader>

      <CardContent className="text-center space-y-6">
        <p className="text-sm text-muted-foreground">
          Didn&apos;t receive it? Check your spam or junk folder.
        </p>

        <Button
          className="w-full"
          disabled={loading || cooldown > 0}
          onClick={handleResend}
          variant="outline"
        >
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" /> Resending...
            </>
          ) : cooldown > 0 ? (
            `Resend in ${cooldown}s`
          ) : (
            "Resend verification email"
          )}
        </Button>

        <Button
          className="w-full"
          variant="link"
          onClick={() => router.push("/login")}
        >
          Back to sign in
        </Button>
      </CardContent>
    </Card>
  );
}

export default function CheckEmailPage() {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <Suspense
        fallback={
          <div className="flex justify-center">
            <Loader2 className="w-10 h-10 animate-spin" />
          </div>
        }
      >
        <CheckEmailForm />
      </Suspense>
    </div>
  );
}
