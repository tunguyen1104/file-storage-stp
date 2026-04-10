"use client";

import { useEffect, useState, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import axios from "axios";

type TokenType = "verify" | "reset";

interface UseSecureTokenOptions {
  type: TokenType;
}

interface UseSecureTokenResult {
  token: string | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Hook that securely handles tokens from email links.
 *
 * When a token is present in the URL:
 * 1. Immediately stores it in an httpOnly cookie via server endpoint
 * 2. Clears the URL (removes token from browser history)
 * 3. Returns the token for use in the component
 *
 * This prevents token exposure in browser history, Referer headers, and logs.
 */
export function useSecureToken({
  type,
}: UseSecureTokenOptions): UseSecureTokenResult {
  const searchParams = useSearchParams();
  const urlToken = searchParams.get("token");

  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const clearUrlToken = useCallback(() => {
    // Remove token from URL without triggering navigation
    const url = new URL(window.location.href);
    url.searchParams.delete("token");
    window.history.replaceState({}, "", url.toString());
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function handleToken() {
      // If token is in URL, store it securely and clear URL
      if (urlToken) {
        try {
          await axios.post("/api/auth/token-session", {
            token: urlToken,
            type,
          });

          if (isMounted) {
            setToken(urlToken);
            clearUrlToken();
            setIsLoading(false);
          }
        } catch {
          if (isMounted) {
            setError("Failed to process token");
            setIsLoading(false);
          }
        }
        return;
      }

      // No URL token — try to retrieve from cookie session
      try {
        const response = await axios.get("/api/auth/token-session");
        if (isMounted) {
          if (response.data.type === type) {
            setToken(response.data.token);
          } else {
            setError("Invalid token type");
          }
          setIsLoading(false);
        }
      } catch {
        if (isMounted) {
          setToken(null);
          setIsLoading(false);
        }
      }
    }

    handleToken();

    return () => {
      isMounted = false;
    };
  }, [urlToken, type, clearUrlToken]);

  return { token, isLoading, error };
}
