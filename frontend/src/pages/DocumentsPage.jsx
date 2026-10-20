// src/pages/DocumentsPage.jsx
// Document viewer with three tabs:
//   1. OCR Text     — raw extracted text in monospace
//   2. Chunks       — each semantic chunk card with metadata
//   3. Semantic Matches — chunks ranked by cosine similarity
// Props: none (self-contained state)

import { useState } from 'react';
import { File, Check, Star, Sparkles } from 'lucide-react';
import { documents, ocrText, docChunks } from '../data/mockData';

// ─── COMPONENT ────────────────────────────────────────────────────────────────
export default function DocumentsPage() {
  const [activeDoc, setActiveDoc] = useState(documents[0]);
  const [tab,       setTab]       = useState('ocr'); // 'ocr' | 'chunks' | 'matches'

  const processed = documents.filter((d) => d.status === 'Processed');

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>

      {/* ── DOC LIST ────────────────────────────────────────────────────── */}
      <aside style={sidebar}>
        <div style={sidebarLabel}>Documents</div>
        {processed.map((doc) => (
          <button
            key={doc.id}
            onClick={() => setActiveDoc(doc)}
            style={{
              ...docBtn,
              background:  activeDoc.id === doc.id ? 'rgba(99,102,241,0.12)' : 'transparent',
              color:        activeDoc.id === doc.id ? '#a5b4fc'               : '#64748b',
            }}
          >
            <File size={13} />
            <span style={docBtnName}>{doc.name}</span>
          </button>
        ))}

        {/* Metadata panel */}
        <div style={metaBox}>
          <div style={metaTitle}>File Info</div>
          {[
            { k: 'Type',       v: activeDoc.type.toUpperCase() },
            { k: 'Size',       v: activeDoc.size               },
            { k: 'Chunks',     v: activeDoc.chunks             },
            { k: 'Embeddings', v: activeDoc.embeddings         },
            { k: 'Status',     v: activeDoc.status             },
            { k: 'Indexed',    v: activeDoc.date               },
          ].map(({ k, v }) => (
            <div key={k} style={metaRow}>
              <span style={metaKey}>{k}</span>
              <span style={metaVal}>{v}</span>
            </div>
          ))}
        </div>
      </aside>

      {/* ── VIEWER AREA ─────────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

        {/* Tabs */}
        <div style={tabBar}>
          {TABS.map(({ id, label }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              style={{
                ...tabBtn,
                color:        tab === id ? '#a5b4fc' : '#64748b',
                background:   tab === id ? 'rgba(99,102,241,0.1)' : 'transparent',
                borderBottom: `2px solid ${tab === id ? '#6366f1' : 'transparent'}`,
              }}
            >
              {id === 'chunks' ? `Chunks (${activeDoc.chunks})` : label}
            </button>
          ))}
        </div>

        {/* Tab content */}
        <div style={viewerBody}>
          {tab === 'ocr'     && <OcrTab     />}
          {tab === 'chunks'  && <ChunksTab  />}
          {tab === 'matches' && <MatchesTab />}
        </div>
      </div>
    </div>
  );
}

// ─── OCR TAB ──────────────────────────────────────────────────────────────────
function OcrTab() {
  return (
    <div>
      {/* Metadata badges */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
        {[
          { icon: Check, text: 'OCR Complete',      color: '#10b981' },
          { icon: Star,  text: 'Confidence: 98.4%', color: '#a5b4fc' },
          { icon: Check, text: 'Engine: Tesseract 5',color: '#a5b4fc'},
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

// ─── CHUNKS TAB ───────────────────────────────────────────────────────────────
function ChunksTab() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {docChunks.map((chunk) => (
        <div key={chunk.id} style={chunkCard}>
          <div style={chunkHeader}>
            <span style={chunkMeta}>Chunk #{chunk.id} · Page {chunk.page}</span>
            <span style={dimBadge}>dim: 1,536</span>
          </div>
          <p style={chunkText}>{chunk.text}</p>

          {/* Token bar (decorative) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10 }}>
            <span style={metaKey}>tokens</span>
            <div style={{ flex: 1, height: 3, background: 'rgba(255,255,255,0.06)', borderRadius: 99, overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${30 + chunk.id * 12}%`, background: '#6366f1', borderRadius: 99 }} />
            </div>
            <span style={metaVal}>{70 + chunk.id * 18}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── SEMANTIC MATCHES TAB ─────────────────────────────────────────────────────
function MatchesTab() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {/* Query context banner */}
      <div style={queryBanner}>
        <Sparkles size={13} color="#a5b4fc" />
        Showing top matches for: &quot;What were the key Q4 revenue metrics?&quot;
      </div>

      {/* Ranked results */}
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
                <span style={{ ...rankBadge, background: rank === 0 ? 'rgba(99,102,241,0.2)' : 'rgba(255,255,255,0.06)', color: rank === 0 ? '#a5b4fc' : '#64748b' }}>
                  #{rank + 1}
                </span>
                <span style={chunkMeta}>Chunk #{chunk.id} · Page {chunk.page}</span>
              </div>

              {/* Similarity bar + score */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 80, height: 5, background: 'rgba(255,255,255,0.08)', borderRadius: 99, overflow: 'hidden' }}>
                  <div style={{
                    height: '100%', borderRadius: 99,
                    width: `${Math.round(chunk.sim * 100)}%`,
                    background: chunk.sim > 0.9 ? '#6366f1' : '#64748b',
                  }} />
                </div>
                <span style={{ fontSize: 12, fontWeight: 800, color: chunk.sim > 0.9 ? '#a5b4fc' : '#64748b' }}>
                  {Math.round(chunk.sim * 100)}%
                </span>
              </div>
            </div>

            <p style={chunkText}>{chunk.text}</p>
          </div>
        ))}
    </div>
  );
}

// ─── CONSTANTS ────────────────────────────────────────────────────────────────
const TABS = [
  { id: 'ocr',     label: 'OCR Text'          },
  { id: 'chunks',  label: 'Chunks'            },
  { id: 'matches', label: 'Semantic Matches'  },
];

// ─── STYLES ───────────────────────────────────────────────────────────────────
const sidebar = {
  width: 210, flexShrink: 0,
  borderRight: '1px solid rgba(255,255,255,0.06)',
  padding: 12, overflowY: 'auto',
  display: 'flex', flexDirection: 'column', gap: 3,
};

const sidebarLabel = {
  fontSize: 10, fontWeight: 700, color: '#64748b',
  textTransform: 'uppercase', letterSpacing: '0.08em',
  marginBottom: 6, padding: '0 4px',
};

const docBtn = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '9px 10px', borderRadius: 9,
  border: 'none', cursor: 'pointer', textAlign: 'left', fontSize: 11, width: '100%',
};

const docBtnName = {
  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
};

const metaBox = {
  marginTop: 16, background: 'rgba(255,255,255,0.03)',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 12, padding: '12px 14px',
};

const metaTitle = { fontSize: 11, fontWeight: 700, color: '#64748b', marginBottom: 10 };
const metaRow   = { display: 'flex', justifyContent: 'space-between', marginBottom: 6 };
const metaKey   = { fontSize: 11, color: '#64748b' };
const metaVal   = { fontSize: 11, color: '#a5b4fc', fontWeight: 600 };

const tabBar = {
  display: 'flex', gap: 2, padding: '14px 20px 0', flexShrink: 0,
  borderBottom: '1px solid rgba(255,255,255,0.06)',
};

const tabBtn = {
  padding: '7px 16px', borderRadius: '8px 8px 0 0',
  border: 'none', cursor: 'pointer', fontSize: 12, fontWeight: 500,
  transition: 'all 0.15s',
};

const viewerBody = {
  flex: 1, overflowY: 'auto', padding: 20,
};

const preStyle = {
  background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 12, padding: 22,
  fontFamily: 'var(--font-mono)', fontSize: 12,
  color: '#cbd5e1', lineHeight: 1.8, whiteSpace: 'pre-wrap', margin: 0,
};

const badge = {
  display: 'flex', alignItems: 'center', gap: 5,
  background: 'rgba(255,255,255,0.04)',
  border: '1px solid rgba(255,255,255,0.08)',
  borderRadius: 8, padding: '5px 11px', fontSize: 11,
};

const chunkCard = {
  background: 'rgba(255,255,255,0.03)',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 12, padding: '14px 18px',
};

const chunkHeader = {
  display: 'flex', justifyContent: 'space-between',
  alignItems: 'center', marginBottom: 10,
};

const chunkMeta  = { fontSize: 11, color: '#64748b' };
const chunkText  = { margin: 0, fontSize: 13, lineHeight: 1.7, color: '#e2e8f0' };
const dimBadge   = {
  fontSize: 11, background: 'rgba(99,102,241,0.1)',
  color: '#a5b4fc', padding: '2px 8px', borderRadius: 6,
};

const queryBanner = {
  display: 'flex', alignItems: 'center', gap: 7,
  background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)',
  borderRadius: 10, padding: '10px 14px', fontSize: 12, color: '#a5b4fc', marginBottom: 4,
};

const rankBadge = {
  fontSize: 10, fontWeight: 800, padding: '2px 7px', borderRadius: 6,
};
