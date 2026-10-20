import { useState, useMemo } from 'react';
import {
  FileText, Image, Music, Video, Volume2, Play,
  Check, LayoutGrid,
} from 'lucide-react';

const TABS = [
  { id: 'ready',   label: 'Ready for Ingestion Submission', count: 5 },
  { id: 'pending', label: 'Pending Admin Ingestion Review', count: 2 },
  { id: 'history', label: 'Submission History',             count: 12 },
];

const FORMAT_FILTERS = [
  { id: 'all',   label: 'All Formats',      icon: LayoutGrid },
  { id: 'text',  label: 'Text (PDF/DOCX)',  icon: FileText },
  { id: 'image', label: 'Visual Assets',    icon: Image },
  { id: 'audio', label: 'Audio Transcripts',icon: Music },
  { id: 'video', label: 'Video Feeds',      icon: Video },
];

const INITIAL_READY = [
  { id: '1', name: 'Annual_Policy_2026.pdf', controller: 'Rahul Kumar', datetime: 'Today, 10:30', size: '1.2 MB', format: 'text', selected: true },
  { id: '2', name: 'System_Architecture_Diagram.png', controller: 'Vikram Patel', datetime: 'Today, 11:15', size: '4.8 MB', format: 'image', selected: false },
  { id: '3', name: 'Safety_Handbook.pdf', controller: 'Anjali Singh', datetime: 'Today, 09:15', size: '3.4 MB', format: 'text', selected: true },
  { id: '4', name: 'Training_Module_Intro.mp4', controller: 'Rahul Kumar', datetime: 'Yesterday, 16:30', size: '85 MB', format: 'video', selected: false },
  { id: '5', name: 'Call_Recording_UserStudy_02.wav', controller: 'Vikram Patel', datetime: 'Yesterday, 14:15', size: '12 MB', format: 'audio', selected: false },
];

const FORMAT_META = {
  text:  { icon: FileText, bg: 'rgba(239,68,68,0.2)',  color: '#ef4444', IconCmp: FileText },
  image: { icon: Image,    bg: 'rgba(16,185,129,0.2)', color: '#10b981', IconCmp: Image },
  audio: { icon: Volume2,  bg: 'rgba(139,92,246,0.2)', color: '#a855f7', IconCmp: Volume2 },
  video: { icon: Play,     bg: 'rgba(139,92,246,0.2)', color: '#a855f7', IconCmp: Play },
};

export default function RequestApproval() {
  const [activeTab, setActiveTab] = useState('ready');
  const [formatFilter, setFormatFilter] = useState('all');
  const [readyDocs, setReadyDocs] = useState(INITIAL_READY);
  const [submittedMessage, setSubmittedMessage] = useState(null);

  const filteredReady = useMemo(
    () => readyDocs.filter((d) => formatFilter === 'all' || d.format === formatFilter),
    [readyDocs, formatFilter]
  );

  const selectedCount = readyDocs.filter((d) => d.selected).length;

  const toggleSelect = (id) => {
    setReadyDocs((prev) =>
      prev.map((d) => (d.id === id ? { ...d, selected: !d.selected } : d))
    );
  };

  const clearSelected = () => {
    setReadyDocs((prev) => prev.map((d) => ({ ...d, selected: false })));
  };

  const submitSelected = () => {
    setSubmittedMessage(`${selectedCount} document(s) submitted to the Knowledge Base staging approval queue.`);
    setReadyDocs((prev) => prev.map((d) => ({ ...d, selected: false })));
    setTimeout(() => setSubmittedMessage(null), 5000);
  };

  return (
    <div style={wrap}>
      <div style={{ marginBottom: 16 }}>
        <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Ingestion Staging &amp; Admin Approval Queue</h2>
        <p style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
          Staged multi-modal documents ready for validation and promotion to active knowledge bases
        </p>
      </div>

      {submittedMessage && (
        <div style={successBox}>
          <Check size={16} color="#10b981" />
          <span>{submittedMessage}</span>
        </div>
      )}

      {/* Tabs */}
      <div style={tabBar}>
        {TABS.map((t) => {
          const active = activeTab === t.id;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setActiveTab(t.id)}
              style={{
                ...tabBtn,
                color: active ? '#f1f5f9' : '#64748b',
                fontWeight: active ? 700 : 500,
                borderBottom: active ? '2px solid #7c3aed' : '2px solid transparent',
              }}
            >
              {t.label} ({t.id === 'ready' ? readyDocs.length : t.count})
            </button>
          );
        })}
      </div>

      {/* Format Filter Bar */}
      {activeTab === 'ready' && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: '#94a3b8' }}>Filter:</span>
          {FORMAT_FILTERS.map((f) => {
            const active = formatFilter === f.id;
            const Icon = f.icon;
            return (
              <button
                key={f.id}
                type="button"
                onClick={() => setFormatFilter(f.id)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  padding: '6px 12px', borderRadius: 99, fontSize: 12, fontWeight: 600,
                  cursor: 'pointer',
                  border: active ? 'none' : '1px solid rgba(255,255,255,0.1)',
                  background: active ? '#7c3aed' : 'rgba(255,255,255,0.04)',
                  color: active ? '#fff' : '#94a3b8',
                  transition: 'all 0.15s',
                }}
              >
                <Icon size={14} />
                {f.label}
              </button>
            );
          })}
        </div>
      )}

      {/* Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 14, paddingBottom: selectedCount > 0 ? 80 : 20 }}>
        {filteredReady.map((doc) => {
          const meta = FORMAT_META[doc.format] || FORMAT_META.text;
          const Icon = meta.icon;
          return (
            <div
              key={doc.id}
              onClick={() => toggleSelect(doc.id)}
              style={{
                ...docCard,
                borderColor: doc.selected ? '#7c3aed' : 'rgba(255,255,255,0.08)',
                background: doc.selected ? 'rgba(124,58,237,0.1)' : 'rgba(255,255,255,0.03)',
                cursor: 'pointer',
              }}
            >
              <div style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
                <div style={{
                  width: 22, height: 22, borderRadius: 6, flexShrink: 0,
                  border: doc.selected ? 'none' : '2px solid rgba(255,255,255,0.25)',
                  background: doc.selected ? '#7c3aed' : 'transparent',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {doc.selected && <Check size={14} color="#fff" strokeWidth={3} />}
                </div>

                <div style={{
                  width: 48, height: 48, borderRadius: 10, flexShrink: 0,
                  background: meta.bg, display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <Icon size={22} color={meta.color} />
                </div>

                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {doc.name}
                  </div>
                  <div style={{ fontSize: 11, color: '#64748b', marginTop: 3 }}>
                    {doc.controller} · {doc.datetime} · {doc.size}
                  </div>
                  <span style={{
                    fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 99,
                    background: 'rgba(16,185,129,0.15)', color: '#10b981', display: 'inline-block', marginTop: 6,
                  }}>
                    Staged &amp; Verified
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Sticky Bottom Action Bar */}
      {selectedCount > 0 && (
        <div style={actionBar}>
          <div style={{ fontSize: 13, color: '#94a3b8' }}>
            <strong style={{ color: '#fff' }}>{selectedCount}</strong> document{selectedCount !== 1 ? 's' : ''} selected for promotion
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={clearSelected} style={ghostBtn}>
              Deselect All
            </button>
            <button onClick={submitSelected} style={primaryBtn}>
              Submit to Knowledge Base Ingestion
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, position: 'relative' };
const tabBar = { display: 'flex', gap: 6, borderBottom: '1px solid rgba(255,255,255,0.08)', marginBottom: 18 };
const tabBtn = { padding: '10px 16px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13 };
const docCard = { border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14, padding: '16px', transition: 'all 0.15s' };
const actionBar = { position: 'sticky', bottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 24px', background: '#161926', border: '1px solid rgba(124,58,237,0.4)', borderRadius: 14, boxShadow: '0 8px 32px rgba(0,0,0,0.6)', zIndex: 30 };
const primaryBtn = { padding: '9px 20px', borderRadius: 8, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontSize: 13, fontWeight: 700, cursor: 'pointer', boxShadow: '0 4px 14px rgba(124,58,237,0.3)' };
const ghostBtn = { padding: '9px 16px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.15)', background: 'transparent', color: '#cbd5e1', fontSize: 13, cursor: 'pointer' };
const successBox = { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 10, background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', color: '#86efac', fontSize: 12, marginBottom: 16 };
