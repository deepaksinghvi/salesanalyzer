import { useState, useRef } from 'react';
import { Upload, FileText, CheckCircle, XCircle, Clock } from 'lucide-react';
import { uploadFile } from '../api/uploads';
import Layout from '../components/Layout';
import type { UploadJob } from '../types';

const PERIOD_TYPES = ['daily', 'monthly', 'quarterly', 'yearly'];

export default function UploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [periodType, setPeriodType] = useState('daily');
  const [status, setStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');
  const [result, setResult] = useState<UploadJob | null>(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFile = (f: File) => {
    if (!f.name.endsWith('.csv')) {
      setErrorMsg('Only CSV files are supported.');
      return;
    }
    setFile(f);
    setErrorMsg('');
    setStatus('idle');
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const f = e.dataTransfer.files[0];
    if (f) handleFile(f);
  };

  const handleSubmit = async () => {
    if (!file) return;
    setStatus('uploading');
    try {
      const res = await uploadFile(file, periodType);
      setResult(res);
      setStatus('success');
    } catch {
      setErrorMsg('Upload failed. Please try again.');
      setStatus('error');
    }
  };

  const reset = () => {
    setFile(null);
    setResult(null);
    setStatus('idle');
    setErrorMsg('');
  };

  return (
    <Layout>
      <div className="p-8 max-w-2xl mx-auto">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Upload Sales Data</h1>
          <p className="text-gray-500 mt-1">Upload a CSV file to process and analyze your sales data.</p>
        </div>

        {status === 'success' && result ? (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-8 text-center">
            <CheckCircle className="text-emerald-500 mx-auto mb-4" size={48} />
            <h2 className="text-xl font-semibold text-gray-900">Upload Successful</h2>
            <p className="text-gray-500 mt-2">Your file is being processed by the orchestrator.</p>
            <div className="mt-6 bg-gray-50 rounded-lg p-4 text-left space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Job ID</span>
                <span className="font-mono text-gray-900 text-xs">{result.jobId}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">File</span>
                <span className="text-gray-900">{result.fileName}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Status</span>
                <span className="inline-flex items-center gap-1 text-amber-600 font-medium">
                  <Clock size={14} /> {result.status}
                </span>
              </div>
            </div>
            <button onClick={reset} className="mt-6 px-6 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
              Upload Another
            </button>
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-8">
            {/* Drop zone */}
            <div
              onClick={() => inputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors ${
                dragging ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-400 hover:bg-gray-50'
              }`}
            >
              <input
                ref={inputRef}
                type="file"
                accept=".csv"
                className="hidden"
                onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
              />
              {file ? (
                <div>
                  <FileText className="text-blue-500 mx-auto mb-3" size={40} />
                  <p className="font-medium text-gray-900">{file.name}</p>
                  <p className="text-sm text-gray-400 mt-1">{(file.size / 1024).toFixed(1)} KB</p>
                </div>
              ) : (
                <div>
                  <Upload className="text-gray-300 mx-auto mb-3" size={40} />
                  <p className="font-medium text-gray-700">Drop your CSV here, or click to browse</p>
                  <p className="text-sm text-gray-400 mt-1">Supports: daily, monthly, quarterly, yearly formats</p>
                </div>
              )}
            </div>

            {errorMsg && (
              <div className="mt-4 flex items-center gap-2 text-red-600 text-sm">
                <XCircle size={16} /> {errorMsg}
              </div>
            )}

            {/* Period selector */}
            <div className="mt-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">Period Type</label>
              <div className="grid grid-cols-4 gap-2">
                {PERIOD_TYPES.map((p) => (
                  <button
                    key={p}
                    onClick={() => setPeriodType(p)}
                    className={`py-2 px-3 rounded-lg text-sm font-medium capitalize border transition-colors ${
                      periodType === p
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-white text-gray-600 border-gray-200 hover:border-blue-400'
                    }`}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>

            {/* CSV format hint */}
            <div className="mt-6 bg-blue-50 rounded-lg p-4">
              <p className="text-xs font-semibold text-blue-800 mb-1">Expected CSV columns</p>
              <p className="text-xs text-blue-700 font-mono">
                tenant_id, transaction_date, category_name, city, region, total_revenue, units_sold
              </p>
            </div>

            <button
              onClick={handleSubmit}
              disabled={!file || status === 'uploading'}
              className="mt-6 w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white font-medium py-2.5 rounded-lg transition-colors text-sm"
            >
              {status === 'uploading' ? 'Uploading...' : 'Upload & Process'}
            </button>
          </div>
        )}
      </div>
    </Layout>
  );
}
