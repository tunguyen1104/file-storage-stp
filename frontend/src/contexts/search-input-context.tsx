"use client";

import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { usePathname, useSearchParams } from "next/navigation";

type SearchInputContextValue = {
  draft: string;
  setDraft: (next: string) => void;
};

const SearchInputContext = createContext<SearchInputContextValue | null>(null);

export function SearchInputProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [draft, setDraft] = useState<string>(() =>
    pathname === "/search" ? searchParams.get("q") ?? "" : ""
  );

  return (
    <SearchInputContext.Provider value={{ draft, setDraft }}>
      {children}
    </SearchInputContext.Provider>
  );
}

export function useSearchInput(): SearchInputContextValue {
  const ctx = useContext(SearchInputContext);
  if (!ctx) {
    throw new Error(
      "useSearchInput must be used within a <SearchInputProvider>"
    );
  }
  return ctx;
}
