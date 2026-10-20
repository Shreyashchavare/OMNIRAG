// src/pages/UploadPage.jsx
// Drag-and-drop upload UI with progress bar and 5-stage pipeline animation.
// No actual file upload happens — fully simulated for demo purposes.
// Props: none (manages its own state)

import { useState } from 'react';
import { Upload, File, Image, Mic, Video, Check, Activity } from 'lucide-react';
import { sampleUploadNames } from '../data/mockData';

// ─── PIPELINE STAGES ──────────────────────────────────────────────────────────
const STAGES = [
  'OCR Extraction',
  'Text Chunking',
  'Embedding Generation',
  'Vector Storage',
  'Index Update',
];

// ─── COMPONENT ────────────────────────────────────────────────────────────────
function createMockUploadFile(name) {
  return {
    id: Date.now(),
    name,
    type: name.split('.').pop(),
    size: `${(Math.random() * 8 + 0.5).toFixed(1)} MB`,
    progress: 0,
    stage: 'uploading',
    done: false,
  };
}

export default function UploadPage() {
  const [uploadFiles, setUploadFiles] = useState([]);
  const [dragOver,    setDragOver]    = useState(false);

  // ── Simulate an upload + processing pipeline ────────────────────────────
  const simulateUpload = (name) => {
    const file = createMockUploadFile(name);
    setUploadFiles((prev) => [file, ...prev]);

    // Phase 1 — upload progress bar
    let p = 0;
    const uploadIv = setInterval(() => {
      p += Math.random() * 18;
      if (p >= 100) {
        p = 100;
        clearInterval(uploadIv);
        setUploadFiles((prev) => prev.map((f) => f.id === file.id ? { ...f, progress: 100 } : f));
        runPipeline(file.id);
      } else {
        setUploadFiles((prev) => prev.map((f) => f.id === file.id ? { ...f, progress: Math.round(p) } : f));
      }
    }, 90);
  };

  // Phase 2 — processing pipeline stages
  const runPipeline = (id) => {
    let si = 0;
    setUploadFiles((prev) => prev.map((f) => f.id === id ? { ...f, stage: STAGES[0] } : f));

    const pipeIv = setInterval(() => {
      si += 1;
      if (si >= STAGES.length) {
        clearInterval(pipeIv);
        setUploadFiles((prev) => prev.map((f) => f.id === id ? { ...f, stage: 'Complete', done: true } : f));
      } else {
        setUploadFiles((prev) => prev.map((f) => f.id === id ? { ...f, stage: STAGES[si] } : f));
      }
    }, 780);
  };

  // ── Drop-zone handlers ──────────────────────────────────────────────────
  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length) {
      files.forEach((f) => simulateUpload(f.name));
    } else {
      simulateUpload(sampleUploadNames[Math.floor(Math.random() * sampleUploadNames.length)]);
    }
  };

  const handleClick = () =>
    simulateUpload(sampleUploadNames[Math.floor(Math.random() * sampleUploadNames.length)]);

  return (
    <div style={wrap}>

      {/* ── DROP ZONE ───────────────────────────────────────────────────── */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={handleClick}
        style={{
          border:     `2px dashed ${dragOver ? '#6366f1' : 'rgba(255,255,255,0.11)'}`,
          borderRadius: 20,
          padding:    '54px 32px',
          textAlign:  'center',
          background: dragOver ? 'rgba(99,102,241,0.06)' : 'rgba(255,255,255,0.02)',
          cursor:     'pointer',
          transition: 'all 0.2s',
        }}
      >
        <div style={uploadIcon}>
          <Upload size={28} color="#6366f1" />
        </div>

        <h3 style={{ fontWeight: 800, fontSize: 18, marginBottom: 8 }}>
          Drop files here or click to upload
        </h3>
        <p style={{ color: '#64748b', fontSize: 13, marginBottom: 20 }}>
          Supports PDF, PNG, JPG, MP3, MP4, WAV, MOV
        </p>

        {/* Type badges */}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
          {TYPE_META.map(({ label, icon: Icon, color }) => (
            <span key={label} style={typeBadge}>
              <Icon size={13} color={color} /> {label}
            </span>
          ))}
        </div>
      </div>

      {/* ── UPLOAD CARDS ────────────────────────────────────────────────── */}
      {uploadFiles.length > 0 && (
        <div>
          <div style={sectionHeader}>
            <Activity size={15} color="#6366f1" />
            Processing Queue
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {uploadFiles.map((file) => (
              <UploadCard key={file.id} file={file} />
            ))}
          </div>
        </div>
      )}

      {/* Empty hint */}
      {uploadFiles.length === 0 && (
        <p style={{ textAlign: 'center', color: '#475569', fontSize: 13, paddingTop: 32 }}>
          Click the zone above or drag any file to start the AI processing pipeline.
        </p>
      )}
    </div>
  );
}

// ─── UPLOAD CARD ──────────────────────────────────────────────────────────────
function UploadCard({ file }) {
  const stageIdx = STAGES.indexOf(file.stage);

  return (
    <div style={cardStyle}>
      {/* File info row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        <div style={fileIcon}>
          <File size={18} color="#6366f1" />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 2 }}>{file.name}</div>
          <div style={{ fontSize: 11, color: '#64748b' }}>{file.size} · .{file.type.toUpperCase()}</div>
        </div>
        <StageBadge done={file.done} stage={file.stage} />
      </div>

      {/* Upload progress bar */}
      {file.stage === 'uploading' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#64748b', marginBottom: 5 }}>
            <span>Uploading...</span>
            <span>{file.progress}%</span>
          </div>
          <div style={progressTrack}>
            <div style={{ ...progressFill, width: `${file.progress}%` }} />
          </div>
        </div>
      )}

      {/* Pipeline pills */}
      {file.stage !== 'uploading' && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {STAGES.map((s, i) => {
            const isDone   = file.done || i < stageIdx;
            const isActive = !file.done && i === stageIdx;
            return (
              <div key={s} style={pill(isDone, isActive)}>
                {isDone
                  ? <Check size={9} color="#10b981" />
                  : <span style={{ width: 5, height: 5, borderRadius: '50%', background: isActive ? '#fbbf24' : '#374151', display: 'inline-block' }} />
                }
                <span style={{ fontSize: 10, color: isDone ? '#10b981' : isActive ? '#fbbf24' : '#475569' }}>{s}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function StageBadge({ done, stage }) {
  return (
    <span style={{
      fontSize: 11, padding: '3px 10px', borderRadius: 99, fontWeight: 700, flexShrink: 0,
      background: done ? 'rgba(16,185,129,0.12)' : 'rgba(251,191,36,0.12)',
      color:      done ? '#10b981'                : '#fbbf24',
    }}>
      {done ? '✓ Complete' : stage}
    </span>
  );
}

// ─── STYLE HELPERS ────────────────────────────────────────────────────────────
const TYPE_META = [
  { label: 'PDF',   icon: File,  color: '#ef4444' },
  { label: 'Image', icon: Image, color: '#10b981' },
  { label: 'Audio', icon: Mic,   color: '#8b5cf6' },
  { label: 'Video', icon: Video, color: '#0369a1' },
];

const wrap = { padding: '24px 28px', maxWidth: 880, display: 'flex', flexDirection: 'column', gap: 24 };

const uploadIcon = {
  width: 58, height: 58, borderRadius: 16,
  background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.22)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  margin: '0 auto 16px',
};

const typeBadge = {
  display: 'flex', alignItems: 'center', gap: 5,
  background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 8, padding: '5px 12px', fontSize: 12, color: '#94a3b8',
};

const sectionHeader = {
  fontWeight: 700, fontSize: 14, marginBottom: 12,
  display: 'flex', alignItems: 'center', gap: 7,
};

const cardStyle = {
  background: 'rgba(255,255,255,0.03)',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: 14, padding: '18px 20px',
  animation: 'fadeIn 0.3s ease forwards',
};

const fileIcon = {
  width: 38, height: 38, borderRadius: 10, flexShrink: 0,
  background: 'rgba(99,102,241,0.12)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

const progressTrack = {
  height: 5, background: 'rgba(255,255,255,0.06)',
  borderRadius: 99, overflow: 'hidden',
};

const progressFill = {
  height: '100%',
  background: 'linear-gradient(90deg,#6366f1,#a855f7)',
  borderRadius: 99, transition: 'width 0.12s',
};

const pill = (done, active) => ({
  display: 'flex', alignItems: 'center', gap: 5,
  padding: '3px 9px', borderRadius: 7,
  background: done  ? 'rgba(16,185,129,0.08)'  : active ? 'rgba(251,191,36,0.08)'  : 'rgba(255,255,255,0.02)',
  border:     `1px solid ${done ? 'rgba(16,185,129,0.15)' : active ? 'rgba(251,191,36,0.15)' : 'rgba(255,255,255,0.05)'}`,
});
