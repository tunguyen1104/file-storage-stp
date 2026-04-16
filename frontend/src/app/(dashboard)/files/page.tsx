"use client";

import { useEffect, useMemo, useState } from "react";
import { FileImage, FileText, Folder, Search, Upload } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

type FileItem = {
  id: string;
  name: string;
  modified: string;
  type: string;
  size: string;
  itemType: "folder" | "pdf" | "image" | "docx";
};

const PAGE_SIZE = 12;
const PAGINATION_THRESHOLD = 12;

function renderFileIcon(itemType: FileItem["itemType"]) {
  if (itemType === "folder") {
    return <Folder className="h-4 w-4 text-amber-500" />;
  }
  if (itemType === "image") {
    return <FileImage className="h-4 w-4 text-sky-500" />;
  }
  return <FileText className="h-4 w-4 text-blue-500" />;
}

export default function FilesPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [fileItems, setFileItems] = useState<FileItem[]>([]);

  useEffect(() => {
    let isMounted = true;

    fetch("/mock-data/files.json")
      .then((response) => response.json())
      .then((data: FileItem[]) => {
        if (!isMounted) return;
        setFileItems(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (isMounted) setFileItems([]);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const shouldPaginate = fileItems.length >= PAGINATION_THRESHOLD;
  const totalPages = shouldPaginate ? Math.ceil(fileItems.length / PAGE_SIZE) : 1;

  const paginatedFiles = useMemo(() => {
    if (!shouldPaginate) return fileItems;
    const startIndex = (currentPage - 1) * PAGE_SIZE;
    return fileItems.slice(startIndex, startIndex + PAGE_SIZE);
  }, [currentPage, fileItems, shouldPaginate]);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(1);
    }
  }, [currentPage, totalPages]);

  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 md:flex-row md:items-center md:justify-between">
        <div className="relative w-full md:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <Input className="h-10 bg-white pl-9" placeholder="Search files and folders..." />
        </div>
        <Button className="gap-2">
          <Upload className="h-4 w-4" />
          Upload File
        </Button>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-gray-200 bg-gray-50/80 hover:bg-gray-50/80">
              <TableHead className="h-11 px-3">Name</TableHead>
              <TableHead className="h-11 px-3">Date Modified</TableHead>
              <TableHead className="h-11 px-3">Type</TableHead>
              <TableHead className="h-11 px-3 text-right">Size</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginatedFiles.map((file) => (
              <TableRow
                key={file.id}
                className="border-b border-gray-100 hover:bg-blue-50/50"
              >
                <TableCell className="px-3 py-2 font-medium">
                  <div className="flex items-center gap-2.5">
                    {renderFileIcon(file.itemType)}
                    <span>{file.name}</span>
                  </div>
                </TableCell>
                <TableCell className="px-3 py-2">{file.modified}</TableCell>
                <TableCell className="px-3 py-2">{file.type}</TableCell>
                <TableCell className="px-3 py-2 text-right">{file.size}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {shouldPaginate && (
          <div className="border-t border-gray-200 px-4 py-3">
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(event) => {
                      event.preventDefault();
                      setCurrentPage((page) => Math.max(1, page - 1));
                    }}
                    className={currentPage === 1 ? "pointer-events-none opacity-50" : ""}
                  />
                </PaginationItem>

                {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
                  <PaginationItem key={page}>
                    <PaginationLink
                      href="#"
                      isActive={page === currentPage}
                      onClick={(event) => {
                        event.preventDefault();
                        setCurrentPage(page);
                      }}
                    >
                      {page}
                    </PaginationLink>
                  </PaginationItem>
                ))}

                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(event) => {
                      event.preventDefault();
                      setCurrentPage((page) => Math.min(totalPages, page + 1));
                    }}
                    className={currentPage === totalPages ? "pointer-events-none opacity-50" : ""}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        )}
      </div>
    </section>
  );
}
