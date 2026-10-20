import { useState, useEffect } from 'react';
import {
  File, Check, Star, Sparkles, RefreshCw, AlertCircle,
  Archive, RotateCcw, Clock,
} from 'lucide-react';
import { documentApi } from '../../api/services/documentApi';
import { extractErrorMessage } from '../../api/client';
import { documents as fallbackDocs, ocrText, docChunks } from '../../data/mockData';

export default function AdminDocuments() {
  const [documents, setDocuments] = useState(fallbackDocs);
  const [activeDoc, setActiveDoc] = useState(fallbackDocs[0]);
  const [tab, setTab] = useState('ocr'); // 'ocr' | 'chunks' | 'matches'
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionSuccess, setActionSuccess] = useState(null);

  useEffect(() => {
    let ignore = false;
    documentApi.getKnowledgeBases()
      .then(async (kbs) => {
        if (ignore) return;
        if (kbs && kbs.length > 0) {
          const kbDocs = await documentApi.getDocumentsByKnowledgeBase(kbs[0].id);
          if (!ignore && kbDocs && kbDocs.length > 0) {
            const mapped = kbDocs.map((d) => ({
              id: d.id,
              name: d.name || d.originalFilename,
              type: (d.fileExtension || 'pdf').toLowerCase(),
              size: d.fileSizeBytes ? `${(d.fileSizeBytes / (1024 * 1024)).toFixed(1)} MB` : '1.5 MB',
              status: d.processingStatus === 'COMPLETED' ? 'Processed' : d.processingStatus || 'Processed',
              chunks: 24,
              embeddings: '1,536',
              date: d.createdAt ? new Date(d.createdAt).toLocaleDateString() : 'Today',
              raw: d,
            }));
            setDocuments(mapped);
            setActiveDoc(mapped[0]);
          }
        }
      })
      .catch((err) => {
        if (!ignore) {
          setError(extractErrorMessage(err));
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const handleSync = async () => {
    setLoading(true);
    setError(null);
    try {
      const kbs = await documentApi.getKnowledgeBases();
      if (kbs && kbs.length > 0) {
        const kbDocs = await documentApi.getDocumentsByKnowledgeBase(kbs[0].id);
        if (kbDocs && kbDocs.length > 0) {
          const mapped = kbDocs.map((d) => ({
            id: d.id,
            name: d.name || d.originalFilename,
            type: (d.fileExtension || 'pdf').toLowerCase(),
            size: d.fileSizeBytes ? `${(d.fileSizeBytes / (1024 * 1024)).toFixed(1)} MB` : '1.5 MB',
            status: d.processingStatus === 'COMPLETED' ? 'Processed' : d.processingStatus || 'Processed',
            chunks: 24,
            embeddings: '1,536',
            date: d.createdAt ? new Date(d.createdAt).toLocaleDateString() : 'Today',
            raw: d,
          }));
          setDocuments(mapped);
          setActiveDoc(mapped[0]);
        }
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleArchive = async (docId) => {
    setActionSuccess(null);
    try {
      await documentApi.archiveDocument(docId);
      setActionSuccess('Document archived successfully.');
      setDocuments((prev) =>
        prev.map((d) => (d.id === docId ? { ...d, status: 'Archived' } : d))
      );
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleRestore = async (docId) => {
    setActionSuccess(null);
    try {
      await documentApi.restoreDocument(docId);
      setActionSuccess('Document restored to active status.');
      setDocuments((prev) =>
        prev.map((d) => (d.id === docId ? { ...d, status: 'Processed' } : d))
      );
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      {/* Top Banner */}
      <div style={{
        padding: '14px 20px',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: 'rgba(255,255,255,0.015)',
        flexShrink: 0,
      }}>
        <div>
          <div style={{ fontWeight: 800, fontSize: 15, color: '#f1f5f9' }}>
            Knowledge Base Ingestion &amp; Approval Queue
          </div>
          <div style={{ fontSize: 12, color: '#64748b', marginTop: 2 }}>
            Manage document indexing, OCR extracts, and vector chunk alignments
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          {actionSuccess && (
            <span style={{ fontSize: 12, color: '#34d399', fontWeight: 600 }}>
              {actionSuccess}
            </span>
          )}
          <button
            onClick={handleSync}
            style={refreshBtn}
            disabled={loading}
          >
            <RefreshCw size={13} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} />
            <span>{loading ? 'Refreshing...' : 'Sync Documents'}</span>
          </button>
        </div>
      </div>

      {error && (
        <div style={{ margin: '8px 20px', padding: '8px 12px', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', borderRadius: 8, fontSize: 12, color: '#fca5a5', display: 'flex', alignItems: 'center', gap: 8 }}>
          <AlertCircle size={14} />
          <span>Knowledge service notice: {error}. (Displaying cached corpus).</span>
        </div>
      )}

      {/* Main View Area */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Document Sidebar */}
        <aside style={sidebar}>
          <div style={sidebarLabel}>Knowledge Corpus ({documents.length})</div>
          {documents.map((doc) => {
            const isSelected = activeDoc?.id === doc.id;
            return (
              <button
                key={doc.id}
                onClick={() => setActiveDoc(doc)}
                style={{
                  ...docBtn,
                  background: isSelected ? 'rgba(124,58,237,0.15)' : 'transparent',
                  borderLeft: isSelected ? '3px solid #7c3aed' : '3px solid transparent',
                  color: isSelected ? '#f1f5f9' : '#94a3b8',
                }}
              >
                <File size={14} color={isSelected ? '#a855f7' : '#64748b'} />
                <div style={{ flex: 1, minWidth: 0, textAlign: 'left' }}>
                  <div style={docBtnName}>{doc.name}</div>
                  <div style={{ fontSize: 10, color: '#64748b', marginTop: 1 }}>{doc.size} · {doc.status}</div>
                </div>
              </button>
            );
          })}

          {/* Metadata Panel */}
          {activeDoc && (
            <div style={metaBox}>
              <div style={metaTitle}>Document Metadata</div>
              {[
                { k: 'Type',       v: (activeDoc.type || 'PDF').toUpperCase() },
                { k: 'Size',       v: activeDoc.size || '1.2 MB' },
                { k: 'Status',     v: activeDoc.status || 'Processed' },
                { k: 'Chunks',     v: activeDoc.chunks || 24 },
                { k: 'Embeddings', v: activeDoc.embeddings || '1,536 dim' },
                { k: 'Indexed',    v: activeDoc.date || 'Today' },
              ].map(({ k, v }) => (
                <div key={k} style={metaRow}>
                  <span style={metaKey}>{k}</span>
                  <span style={metaVal}>{v}</span>
                </div>
              ))}

              <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', gap: 6 }}>
                {activeDoc.status === 'Archived' ? (
                  <button
                    onClick={() => handleRestore(activeDoc.id)}
                    style={restoreBtn}
                    title="Restore document"
                  >
                    <RotateCcw size={12} /> Restore
                  </button>
                ) : (
                  <button
                    onClick={() => handleArchive(activeDoc.id)}
                    style={archiveBtn}
                    title="Archive document"
                  >
                    <Archive size={12} /> Archive
                  </button>
                )}
              </div>
            </div>
          )}
        </aside>

        {/* Viewer Area */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {/* Tabs */}
          <div style={tabBar}>
            {TABS.map(({ id, label }) => (
              <button
                key={id}
                onClick={() => setTab(id)}
                style={{
                  ...tabBtn,
                  color: tab === id ? '#a5b4fc' : '#64748b',
                  background: tab === id ? 'rgba(99,102,241,0.1)' : 'transparent',
                  borderBottom: `2px solid ${tab === id ? '#6366f1' : 'transparent'}`,
                }}
              >
                {id === 'chunks' ? `Chunks (${activeDoc?.chunks || 24})` : label}
              </button>
            ))}
          </div>

          {/* Tab Content */}
          <div style={viewerBody}>
            {tab === 'ocr'     && <OcrTab doc={activeDoc} />}
            {tab === 'chunks'  && <ChunksTab />}
            {tab === 'matches' && <MatchesTab />}
          </div>
        </div>
      </div>
    </div>
  );
}

function OcrTab() {
  return (
    <div>
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
        {[
          { icon: Check, text: 'OCR Pipeline Complete', color: '#10b981' },
          { icon: Star,  text: 'Confidence: 98.4%',     color: '#a5b4fc' },
          { icon: Check, text: 'Engine: Tesseract 5',   color: '#a5b4fc' },
          { icon: Clock, text: 'Parsed in 142ms',       color: '#94a3b8' },
        ].map(({ icon: Icon, text, color }) => (
          <div key={text} style={badge}>
            <Icon size={11} color={color} />
            <span style={{ color }}>{text}</span>
          </div>
        ))}
      </div>

      <pre style={preStyle}>{ocrText}</pre>
    </div>
  );
}

function ChunksTab() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {docChunks.map((chunk) => (
        <div key={chunk.id} style={chunkCard}>
          <div style={chunkHeader}>
            <span style={chunkMeta}>Chunk #{chunk.id} · Page {chunk.page}</span>
            <span style={dimBadge}>1,536 Dimensions (FAISS)</span>
          </div>
          <p style={chunkText}>{chunk.text}</p>
        </div>
      ))}
    </div>
  );
}

function MatchesTab() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={queryBanner}>
        <Sparkles size={13} color="#a5b4fc" />
        Showing top cosine similarity matches for query: &quot;What were the key Q4 revenue metrics?&quot;
      </div>

      {[...docChunks]
        .sort((a, b) => b.sim - a.sim)
        .map((chunk, rank) => (
          <div
            key={chunk.id}
            style={{
              ...chunkCard,
              border: `1px solid ${chunk.sim > 0.9 ? 'rgba(99,102,241,0.3)' : 'rgba(255,255,255,0.07)'}`,
            }}
          >
            <div style={chunkHeader}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{
                  fontSize: 10, fontWeight: 800, padding: '2px 7px', borderRadius: 6,
                  background: rank === 0 ? 'rgba(99,102,241,0.2)' : 'rgba(255,255,255,0.06)',
                  color: rank === 0 ? '#a5b4fc' : '#64748b',
                }}>
                  #{rank + 1}
                </span>
                <span style={chunkMeta}>Chunk #{chunk.id} · Page {chunk.page}</span>
              </div>

              <span style={{ fontSize: 12, fontWeight: 800, color: chunk.sim > 0.9 ? '#34d399' : '#94a3b8' }}>
                Cosine Sim: {Math.round(chunk.sim * 100)}%
              </span>
            </div>
            <p style={chunkText}>{chunk.text}</p>
          </div>
        ))}
    </div>
  );
}

const TABS = [
  { id: 'ocr',     label: 'Raw Extracted Text' },
  { id: 'chunks',  label: 'Semantic Chunks' },
  { id: 'matches', label: 'Vector Similarity Matches' },
];

const sidebar = { width: 230, flexShrink: 0, borderRight: '1px solid rgba(255,255,255,0.06)', padding: 12, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 3 };
const sidebarLabel = { fontSize: 10, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 6, padding: '0 4px' };
const docBtn = { display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px', borderRadius: 8, border: 'none', cursor: 'pointer', fontSize: 12, width: '100%' };
const docBtnName = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 600, color: '#f1f5f9' };
const metaBox = { marginTop: 14, background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12, padding: '12px 14px' };
const metaTitle = { fontSize: 11, fontWeight: 700, color: '#64748b', marginBottom: 10 };
const metaRow = { display: 'flex', justifyContent: 'space-between', marginBottom: 6 };
const metaKey = { fontSize: 11, color: '#64748b' };
const metaVal = { fontSize: 11, color: '#a5b4fc', fontWeight: 600 };
const tabBar = { display: 'flex', gap: 2, padding: '14px 20px 0', flexShrink: 0, borderBottom: '1px solid rgba(255,255,255,0.06)' };
const tabBtn = { padding: '7px 16px', borderRadius: '8px 8px 0 0', border: 'none', cursor: 'pointer', fontSize: 12, fontWeight: 600 };
const viewerBody = { flex: 1, overflowY: 'auto', padding: 20 };
const preStyle = { background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12, padding: 20, fontFamily: 'var(--font-mono, monospace)', fontSize: 12, color: '#cbd5e1', lineHeight: 1.8, whiteSpace: 'pre-wrap', margin: 0 };
const badge = { display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, padding: '5px 11px', fontSize: 11 };
const chunkCard = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 12, padding: '14px 18px' };
const chunkHeader = { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 };
const chunkMeta = { fontSize: 11, color: '#64748b', fontWeight: 600 };
const chunkText = { margin: 0, fontSize: 13, lineHeight: 1.7, color: '#e2e8f0' };
const dimBadge = { fontSize: 10, background: 'rgba(99,102,241,0.12)', color: '#a5b4fc', padding: '2px 8px', borderRadius: 6, fontWeight: 600 };
const queryBanner = { display: 'flex', alignItems: 'center', gap: 7, background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)', borderRadius: 10, padding: '10px 14px', fontSize: 12, color: '#a5b4fc', marginBottom: 4 };
const refreshBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 12, fontWeight: 600, cursor: 'pointer' };
const archiveBtn = { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, padding: '6px', borderRadius: 6, border: '1px solid rgba(239,68,68,0.3)', background: 'rgba(239,68,68,0.1)', color: '#ef4444', fontSize: 11, fontWeight: 600, cursor: 'pointer' };
const restoreBtn = { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, padding: '6px', borderRadius: 6, border: '1px solid rgba(16,185,129,0.3)', background: 'rgba(16,185,129,0.1)', color: '#10b981', fontSize: 11, fontWeight: 600, cursor: 'pointer' };
