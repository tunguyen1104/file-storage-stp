export default function FileDetailPage({
  params,
}: {
  params: Promise<{ fileId: string }>;
}) {
  // TODO: Use useFile(fileId) hook to fetch file detail
  // TODO: Render <FilePreviewModal />, <FileActionsMenu />, metadata
  return (
    <div>
      <h1 className="text-2xl font-bold">File Detail</h1>
      <p className="text-muted-foreground">View file metadata and actions.</p>
    </div>
  );
}
