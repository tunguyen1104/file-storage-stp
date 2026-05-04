"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Loader2, Pencil } from "lucide-react";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { renameFolder } from "@/lib/api/folder";

const formSchema = z.object({
  name: z
    .string()
    .min(1, "Folder name is required")
    .max(255, "Folder name must be less than 255 characters"),
});

type FormValues = z.infer<typeof formSchema>;

interface RenameFolderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  folderId: string;
  currentName: string;
  onSuccess: () => void;
}

export function RenameFolderDialog({
  open,
  onOpenChange,
  folderId,
  currentName,
  onSuccess,
}: RenameFolderDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { name: currentName },
  });

  // Reset to the current folder name whenever the dialog opens
  useEffect(() => {
    if (open) {
      form.reset({ name: currentName });
    }
  }, [open, currentName, form]);

  async function onSubmit(data: FormValues) {
    if (data.name === currentName) {
      onOpenChange(false);
      return;
    }
    setIsSubmitting(true);
    try {
      await renameFolder(folderId, data.name);
      toast.success("Folder renamed successfully");
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to rename folder"
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Pencil className="h-5 w-5 text-[#7C3AED]" />
            Rename Folder
          </DialogTitle>
          <DialogDescription>
            Enter a new name for &ldquo;{currentName}&rdquo;.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4 py-4"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Folder Name</FormLabel>
                  <FormControl>
                    <Input autoFocus {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting}
                className="bg-[#7C3AED] hover:bg-[#6D28D9]"
              >
                {isSubmitting && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                Rename
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
