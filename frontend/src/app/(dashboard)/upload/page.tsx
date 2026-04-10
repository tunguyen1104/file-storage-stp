export default function UploadPage() {
  // TODO: Use useFileUpload() hook
  // TODO: Render <UploadZone />, <UploadProgress />
  return (
    <div>
      <h1 className="text-2xl font-bold">Upload Files</h1>
      <p className="text-muted-foreground">
        Drag and drop files or click to browse. Files larger than 100MB use chunked upload.
      </p>
    </div>
  );
}
