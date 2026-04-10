import React from "react";
import { Button } from "@/components/ui/button";
import { Languages } from "lucide-react";

const LanguageToggle = () => {
  return (
    <Button
      variant="ghost"
      className="rounded-full transition-colors hover:bg-white/10 border border-ring hover:shadow-md"
      aria-label="Toggle language"
    >
      <Languages className="h-4 w-4" />
    </Button>
  );
};
export default LanguageToggle;
