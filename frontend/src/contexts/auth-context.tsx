"use client";

import React, { createContext, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import type { Role } from "@/lib/constants";

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  fullName: string;
  role: Role;
  tenantId: string;
  avatarUrl?: string;
}

interface AuthContextState {
  user: AuthUser | null;
  isLoading: boolean;
  isValidating: boolean;
  signIn: (email: string, password: string) => Promise<AuthUser>;
  logOut: () => Promise<void>;
  mutateUser: () => Promise<AuthUser | null>;
}

const MOCK_AUTH_STORAGE_KEY = "mock-auth-user";

function resolveRoleByEmail(email: string): Role {
  const normalizedEmail = email.toLowerCase();
  if (normalizedEmail.includes("super")) return "SUPER_ADMIN";
  if (normalizedEmail.includes("admin")) return "ADMIN_TENANT";
  return "USER";
}

export const AuthContext = createContext<AuthContextState | undefined>(
  undefined
);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    try {
      const storedUser = window.localStorage.getItem(MOCK_AUTH_STORAGE_KEY);
      if (storedUser) {
        setUser(JSON.parse(storedUser) as AuthUser);
      }
    } catch {
      window.localStorage.removeItem(MOCK_AUTH_STORAGE_KEY);
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      await Promise.resolve(password);
      const role = resolveRoleByEmail(email);
      const fakeUser: AuthUser = {
        id: "1",
        name: "John Doe",
        email,
        fullName: "John Doe",
        role,
        tenantId: role === "SUPER_ADMIN" ? "platform" : "tenant-001",
      };

      setUser(fakeUser);
      window.localStorage.setItem(MOCK_AUTH_STORAGE_KEY, JSON.stringify(fakeUser));
      return fakeUser;
    },
    []
  );

  const logOut = useCallback(async () => {
    setUser(null);
    window.localStorage.removeItem(MOCK_AUTH_STORAGE_KEY);
    router.push("/login");
  }, [router]);

  const mutateUser = useCallback(async () => {
    return user;
  }, [user]);

  const contextValue = useMemo(
    () => ({
      user,
      isLoading,
      isValidating: false,
      signIn,
      logOut,
      mutateUser,
    }),
    [user, isLoading, signIn, logOut, mutateUser]
  );

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}