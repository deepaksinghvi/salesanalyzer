import { useState, useRef } from 'react';
import { Upload, FileText, CheckCircle, XCircle, Clock, Trash2 } from 'lucide-react';
import { uploadFile } from '../api/uploads';
import Layout from '../components/Layout';
import type { UploadJob } from '../types';

const PERIOD_TYPES = ['daily', 'monthly', 'quarterly', 'yearly'];

interface FileEntry {
  file: File;
  status: 'pending' | 'uploading' | 'success' | 'error';
  result?: UploadJob;
  error?: string;
}

export default function UploadPage() {
  const [files, setFiles] = useState<FileEntry[]>([]);
  const [periodType, setPeriodType] = useState('daily');
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const addFiles = (newFiles: FileList | File[]) => {
    const entries: FileEntry[] = [];
    for (const f of Array.from(newFiles)) {
      if (!f.name.endsWith('.csv')) continue;
      if (files.some((e) => e.file.name === f.name && e.file.size === f.size)) continue;
      entries.push({ file: f, status: 'pending' });
    }
    if (entries.length > 0) {
      setFiles((prev) => [...prev, ...entries]);
    }
  };

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    addFiles(e.dataTransfer.files);
  };

  const handleSubmit = async () => {
    if (files.length === 0 || uploading) return;
    setUploading(true);

    for (let i = 0; i < files.length; i++) {
      if (files[i].status === 'success') continue;

      setFiles((prev) =>
        prev.map((f, idx) => (idx === i ? { ...f, status: 'uploading' } : f))
      );

      try {
        const res = await uploadFile(files[i].file, periodType);
        setFiles((prev) =>
          prev.map((f, idx) => (idx === i ? { ...f, status: 'success', result: res } : f))
        );
      } catch {
        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i ? { ...f, status: 'error', error: 'Upload failed' } : f
          )
        );
      }
    }

    setUploading(false);
  };

  const reset = () => {
    setFiles([]);
    setUploading(false);
  };

  const pendingCount = files.filter((f) => f.status === 'pending').length;
  const successCount = files.filter((f) => f.status === 'success').length;
  const errorCount = files.filter((f) => f.status === 'error').length;
  const allDone = files.length > 0 && pendingCount === 0 && !uploading;

  return (
    <Layout>
      <div className="p-8 max-w-2xl mx-auto">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Upload Sales Data</h1>
          <p className="text-gray-500 mt-1">
            Upload one or more CSV files to process and analyze your sales data.
          </p>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-8">
          {/* Drop zone */}
          <div
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors ${
              dragging
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200 hover:border-blue-400 hover:bg-gray-50'
            }`}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".csv"
              multiple
              className="hidden"
              onChange={(e) => {
                if (e.target.files) addFiles(e.target.files);
                e.target.value = '';
              }}
            />
            <Upload className="text-gray-300 mx-auto mb-3" size={40} />
            <p className="font-medium text-gray-700">
              Drop CSV files here, or click to browse
            </p>
            <p className="text-sm text-gray-400 mt-1">
              Select multiple files at once or add them one by one
            </p>
          </div>

          {/* File list */}
          {files.length > 0 && (
            <div className="mt-6 space-y-2">
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium text-gray-700">
                  {files.length} file{files.length !== 1 ? 's' : ''} selected
                </p>
                {!uploading && (
                  <button
                    onClick={reset}
                    className="text-xs text-gray-400 hover:text-red-500 transition-colors"
                  >
                    Clear all
                  </button>
                )}
              </div>
              {files.map((entry, idx) => (
                <div
                  key={`${entry.file.name}-${idx}`}
                  className="flex items-center gap-3 bg-gray-50 rounded-lg px-4 py-3"
                >
                  <FileText
                    size={18}
                    className={
                      entry.status === 'success'
                        ? 'text-emerald-500'
                        : entry.status === 'error'
                        ? 'text-red-500'
                        : entry.status === 'uploading'
                        ? 'text-blue-500 animate-pulse'
                        : 'text-gray-400'
                    }
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">
                      {entry.file.name}
                    </p>
                    <p className="text-xs text-gray-400">
                      {(entry.file.size / 1024).toFixed(1)} KB
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {entry.status === 'pending' && (
                      <span className="text-xs text-gray-400">Pending</span>
                    )}
                    {entry.status === 'uploading' && (
                      <span className="text-xs text-blue-600 flex items-center gap-1">
                        <Clock size={12} className="animate-spin" /> Uploading...
                      </span>
                    )}
                    {entry.status === 'success' && (
                      <span className="text-xs text-emerald-600 flex items-center gap-1">
                        <CheckCircle size={12} /> Done
                      </span>
                    )}
                    {entry.status === 'error' && (
                      <span className="text-xs text-red-600 flex items-center gap-1">
                        <XCircle size={12} /> Failed
                      </span>
                    )}
                    {entry.status === 'pending' && !uploading && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          removeFile(idx);
                        }}
                        className="p-1 text-gray-300 hover:text-red-500 transition-colors"
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Summary after all done */}
          {allDone && (
            <div className="mt-4 bg-gray-50 rounded-lg p-4 text-sm">
              <div className="flex gap-4">
                {successCount > 0 && (
                  <span className="text-emerald-600 font-medium">
                    {successCount} uploaded
                  </span>
                )}
                {errorCount > 0 && (
                  <span className="text-red-600 font-medium">{errorCount} failed</span>
                )}
              </div>
              {successCount > 0 && (
                <p className="text-gray-500 mt-1">
                  Files are being processed by the orchestrator. Check the dashboard for
                  updated data.
                </p>
              )}
            </div>
          )}

          {/* Period selector */}
          <div className="mt-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Period Type
            </label>
            <div className="grid grid-cols-4 gap-2">
              {PERIOD_TYPES.map((p) => (
                <button
                  key={p}
                  onClick={() => setPeriodType(p)}
                  disabled={uploading}
                  className={`py-2 px-3 rounded-lg text-sm font-medium capitalize border transition-colors ${
                    periodType === p
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-600 border-gray-200 hover:border-blue-400'
                  } disabled:opacity-50`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          {/* CSV format hint */}
          <div className="mt-6 bg-blue-50 rounded-lg p-4">
            <p className="text-xs font-semibold text-blue-800 mb-1">
              Expected CSV columns
            </p>
            <p className="text-xs text-blue-700 font-mono">
              tenant_id, transaction_date, category_name, city, region, total_revenue,
              units_sold
            </p>
          </div>

          <button
            onClick={allDone ? reset : handleSubmit}
            disabled={files.length === 0 || uploading}
            className="mt-6 w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
          >
            {uploading
              ? `Uploading ${files.filter((f) => f.status === 'uploading').length > 0 ? files.findIndex((f) => f.status === 'uploading') + 1 : '...'} of ${files.length}...`
              : allDone
              ? 'Upload More Files'
              : `Upload ${files.length} File${files.length !== 1 ? 's' : ''}`}
          </button>
        </div>
      </div>
    </Layout>
  );
}
