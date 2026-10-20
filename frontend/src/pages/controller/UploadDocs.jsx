import { useState, useEffect } from 'react';
import {
  Upload, FileText, Image, Mic, Video, CheckCircle2,
  AlertCircle, Loader2, ArrowRight, RefreshCw,
} from 'lucide-react';
import { documentApi } from '../../api/services/documentApi';
import { processingApi } from '../../api/services/processingApi';
import { useAuth } from '../../contexts/AuthContext';
import { extractErrorMessage } from '../../api/client';

const MODULES = [
  {
    id: 'documents',
    title: 'Document Corpus',
    description: 'Process PDFs, Word Files, Text',
    formats: ['PDF', 'DOCX', 'TXT'],
    icon: FileText,
    color: '#6366f1',
  },
  {
    id: 'visual',
    title: 'Visual Assets',
    description: 'Analyze Photos, Charts, Diagrams',
    formats: ['PNG', 'JPG', 'BMP'],
    icon: Image,
    color: '#10b981',
  },
  {
    id: 'audio',
    title: 'Audio Streams',
    description: 'Transcribe Audio Logs, Interviews',
    formats: ['MP3', 'WAV', 'FLAC'],
    icon: Mic,
    color: '#f59e0b',
  },
  {
    id: 'video',
    title: 'Video Content',
    description: 'Index and Analyze Video Feeds',
    formats: ['MP4', 'MOV', 'AVI'],
    icon: Video,
    color: '#a855f7',
  },
];

export default function UploadDocs() {
  const { user } = useAuth();
  const [selectedModule, setSelectedModule] = useState(MODULES[0]);
  const [filename, setFilename] = useState('Q2_Finance_Report.pdf');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [activeJob, setActiveJob] = useState(null);
  const [jobProgress, setJobProgress] = useState(null);
  const [error, setError] = useState(null);
  const [recentJobs, setRecentJobs] = useState([]);

  // Fetch recent jobs
  const fetchRecentJobs = async () => {
    try {
      const data = await processingApi.getJobs(null, 0, 8);
      if (data?.content) {
        setRecentJobs(data.content);
      }
    } catch {
      // Quietly fall back
    }
  };

  useEffect(() => {
    let ignore = false;
    processingApi.getJobs(null, 0, 8)
      .then((data) => {
        if (!ignore && data?.content) {
          setRecentJobs(data.content);
        }
      })
      .catch(() => {
        // Quietly fall back
      });
    return () => {
      ignore = true;
    };
  }, []);

  // Poll active job status
  useEffect(() => {
    if (!activeJob?.id) return undefined;
    const interval = setInterval(async () => {
      try {
        const statusData = await processingApi.getJobStatus(activeJob.id);
        setJobProgress(statusData);
        if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED' || statusData.status === 'CANCELLED') {
          clearInterval(interval);
          fetchRecentJobs();
        }
      } catch {
        // Keep polling
      }
    }, 2500);

    return () => clearInterval(interval);
  }, [activeJob?.id]);

  const handleStartPipeline = async (e) => {
    e?.preventDefault();
    if (!filename.trim()) {
      setError('Please provide a document filename.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setActiveJob(null);
    setJobProgress(null);

    try {
      // Step 1: Create or fetch knowledge base
      let kbId;
      try {
        const kbs = await documentApi.getKnowledgeBases();
        if (kbs && kbs.length > 0) {
          kbId = kbs[0].id;
        } else {
          const newKb = await documentApi.createKnowledgeBase({
            name: 'Default Knowledge Base',
            description: 'Primary workspace corpus',
            slug: 'default-kb',
          });
          kbId = newKb.id;
        }
      } catch {
        kbId = '00000000-0000-0000-0000-000000000001';
      }

      // Step 2: Register Document in Knowledge Service
      const ext = filename.split('.').pop() || 'pdf';
      const docRecord = await documentApi.createDocument({
        knowledgeBaseId: kbId,
        name: filename.trim(),
        originalFilename: filename.trim(),
        storedFilename: `omnirag_${Date.now()}_${filename.trim()}`,
        storagePath: `minio://omnirag-corpus/${filename.trim()}`,
        fileExtension: ext,
        mimeType: ext === 'pdf' ? 'application/pdf' : 'text/plain',
        fileSizeBytes: 2048576,
        checksum: 'sha256_mock_hash_for_verification',
        ownerId: user?.userId || '00000000-0000-0000-0000-000000000001',
      });

      // Step 3: Trigger Processing Job
      const job = await processingApi.createJob({
        document_id: docRecord.id,
        knowledge_base_id: kbId,
        idempotency_key: `job_${docRecord.id}_${Date.now()}`,
      });

      setActiveJob(job);
      setJobProgress({
        status: job.status || 'QUEUED',
        stage: job.stage || 'INGESTION',
        currentStep: 'Job enqueued for extraction',
      });
      fetchRecentJobs();
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={wrap}>
      {/* Page Header */}
      <div>
        <h2 style={{ fontWeight: 800, fontSize: 22, color: '#f1f5f9' }}>Multi-Modal Document Ingestion</h2>
        <p style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
          Orchestrate OCR text extraction, chunk splitting, embedding generation, and vector indexing
        </p>
      </div>

      {error && (
        <div style={errorBox}>
          <AlertCircle size={15} color="#ef4444" style={{ flexShrink: 0 }} />
          <span>Pipeline Error: {error}</span>
        </div>
      )}

      {/* Module Selector */}
      <div>
        <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'block', marginBottom: 12 }}>
          1. Select Ingestion Module
        </span>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
          {MODULES.map((mod) => {
            const isSelected = selectedModule.id === mod.id;
            const Icon = mod.icon;
            return (
              <div
                key={mod.id}
                onClick={() => setSelectedModule(mod)}
                style={{
                  ...moduleCard,
                  borderColor: isSelected ? mod.color : 'rgba(255,255,255,0.08)',
                  background: isSelected ? `${mod.color}10` : 'rgba(255,255,255,0.03)',
                  cursor: 'pointer',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                  <div style={{
                    width: 34, height: 34, borderRadius: 8,
                    background: `${mod.color}20`, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <Icon size={18} color={mod.color} />
                  </div>
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>{mod.title}</span>
                </div>
                <p style={{ fontSize: 12, color: '#64748b', margin: '0 0 10px', lineHeight: 1.4 }}>{mod.description}</p>
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  {mod.formats.map((f) => (
                    <span key={f} style={formatTag}>{f}</span>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Upload & Pipeline Trigger Form */}
      <div style={card}>
        <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'block', marginBottom: 14 }}>
          2. Document Registration &amp; Pipeline Launch
        </span>

        <form onSubmit={handleStartPipeline} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ fontSize: 12, color: '#94a3b8', display: 'block', marginBottom: 6, fontWeight: 600 }}>
              Document Filename (with extension)
            </label>
            <input
              value={filename}
              onChange={(e) => setFilename(e.target.value)}
              placeholder="e.g. Compliance_Report_2026.pdf"
              style={inputStyle}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, paddingTop: 6 }}>
            <span style={{ fontSize: 12, color: '#64748b' }}>
              Target Pipeline: <strong>{selectedModule.title}</strong> (Auto-extract + FAISS HNSW Embedding)
            </span>

            <button
              type="submit"
              disabled={isSubmitting}
              style={{ ...submitBtn, opacity: isSubmitting ? 0.7 : 1 }}
            >
              {isSubmitting ? (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Loader2 size={16} style={{ animation: 'spin 0.8s linear infinite' }} /> Initializing Pipeline...
                </span>
              ) : (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Upload size={16} /> START MULTI-MODAL PIPELINE <ArrowRight size={15} />
                </span>
              )}
            </button>
          </div>
        </form>

        {/* Live Active Pipeline Tracker */}
        {activeJob && (
          <div style={activeJobCard}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <CheckCircle2 size={16} color="#34d399" />
                <span style={{ fontWeight: 700, fontSize: 13, color: '#f1f5f9' }}>
                  Active Job ID: <code style={{ color: '#c084fc' }}>{activeJob.id}</code>
                </span>
              </div>
              <span style={{
                fontSize: 11, padding: '3px 9px', borderRadius: 99, fontWeight: 700,
                background: jobProgress?.status === 'COMPLETED' ? 'rgba(16,185,129,0.15)' : 'rgba(99,102,241,0.15)',
                color: jobProgress?.status === 'COMPLETED' ? '#10b981' : '#a5b4fc',
              }}>
                {jobProgress?.status || 'QUEUED'}
              </span>
            </div>

            <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 8 }}>
              Stage: <strong style={{ color: '#f1f5f9' }}>{jobProgress?.stage || 'INGESTION'}</strong> · Step: {jobProgress?.currentStep || 'Initializing'}
            </div>

            <div style={{ height: 6, background: 'rgba(255,255,255,0.06)', borderRadius: 99, overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: jobProgress?.status === 'COMPLETED' ? '100%' : '65%',
                background: 'linear-gradient(90deg, #7c3aed, #60a5fa)',
                borderRadius: 99,
                transition: 'width 0.4s ease',
              }} />
            </div>
          </div>
        )}
      </div>

      {/* Processing Service Queue Table */}
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>
            RAG Ingestion Queue &amp; Orchestrator Jobs
          </span>
          <button onClick={fetchRecentJobs} style={refreshBtn}>
            <RefreshCw size={12} /> Sync Queue
          </button>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              {['Status', 'Job ID', 'Document UUID', 'Stage', 'Attempts', 'Created'].map((h) => (
                <th key={h} style={thStyle}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {recentJobs.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>
                  No active or past jobs found in Processing Service. Launch a new pipeline above.
                </td>
              </tr>
            ) : (
              recentJobs.map((j) => (
                <tr key={j.id}>
                  <td style={tdStyle}>
                    <span style={{
                      fontSize: 10, padding: '2px 8px', borderRadius: 99, fontWeight: 700,
                      background: j.status === 'COMPLETED' ? 'rgba(16,185,129,0.15)' : j.status === 'FAILED' ? 'rgba(239,68,68,0.15)' : 'rgba(99,102,241,0.15)',
                      color: j.status === 'COMPLETED' ? '#10b981' : j.status === 'FAILED' ? '#ef4444' : '#a5b4fc',
                    }}>
                      {j.status}
                    </span>
                  </td>
                  <td style={tdStyle}><code>{j.id.slice(0, 8)}...</code></td>
                  <td style={tdStyle}><code>{j.documentId.slice(0, 8)}...</code></td>
                  <td style={tdStyle}><span style={{ fontWeight: 600, color: '#f1f5f9' }}>{j.stage}</span></td>
                  <td style={tdStyle}>{j.attemptCount || 1} / {j.maxAttempts || 3}</td>
                  <td style={tdStyle}>{j.createdAt ? new Date(j.createdAt).toLocaleTimeString() : 'Recent'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, display: 'flex', flexDirection: 'column', gap: 20 };
const card = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '20px' };
const moduleCard = { border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14, padding: '16px', transition: 'all 0.2s' };
const formatTag = { fontSize: 10, padding: '2px 7px', borderRadius: 6, background: 'rgba(255,255,255,0.06)', color: '#94a3b8', fontWeight: 600 };
const inputStyle = { width: '100%', padding: '11px 14px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 13, outline: 'none', boxSizing: 'border-box' };
const submitBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '11px 24px', borderRadius: 10, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontWeight: 700, fontSize: 13, cursor: 'pointer', boxShadow: '0 4px 16px rgba(124,58,237,0.3)' };
const activeJobCard = { marginTop: 18, padding: '16px', borderRadius: 12, background: 'rgba(124,58,237,0.08)', border: '1px solid rgba(124,58,237,0.3)' };
const thStyle = { textAlign: 'left', padding: '10px 12px', background: 'rgba(255,255,255,0.04)', fontSize: 11, textTransform: 'uppercase', color: '#64748b' };
const tdStyle = { padding: '12px', fontSize: 12, borderBottom: '1px solid rgba(255,255,255,0.05)', color: '#94a3b8' };
const refreshBtn = { display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', borderRadius: 6, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#a5b4fc', fontSize: 11, cursor: 'pointer' };
const errorBox = { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 10, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#fca5a5', fontSize: 12 };
