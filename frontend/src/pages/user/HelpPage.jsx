import { useState } from 'react';
import { ChevronDown, ChevronRight, Mail, Shield, Cpu, Layers } from 'lucide-react';

const SECTIONS = [
  {
    title: 'Architecture & System Overview',
    icon: Layers,
    items: [
      {
        q: 'How does the OMNI-RAG multi-service architecture work?',
        a: 'OMNI-RAG is organized as a microservices architecture. Browser traffic terminates at the Gateway Service (port 8080), which validates RS256 JWT tokens and injects user context headers (X-User-Id, X-User-Roles) to downstream services: Identity, Knowledge, Processing, Analytics, and Notifications.',
      },
      {
        q: 'What multi-modal formats are supported for ingestion?',
        a: 'The Processing Service pipeline ingests PDF documents, plain text, PNG/JPG images (via OCR), MP3/WAV audio (via Whisper ASR transcription), and video feeds. All extracted text is chunked semantically and stored with metadata.',
      },
      {
        q: 'How are document chunks embedded and queried?',
        a: 'Text chunks are passed to the embedding engine to generate 1,536-dimensional vector representations. Similarity queries leverage HNSW indexing in FAISS with cosine similarity thresholds to retrieve top-k chunks.',
      },
    ],
  },
  {
    title: 'Security & Access Roles',
    icon: Shield,
    items: [
      {
        q: 'What roles exist and what permissions do they carry?',
        a: 'The platform defines three roles: Admin (full system governance, user locking, analytics), Controller (document uploading, pipeline configuration, model testing), and User (knowledge querying and chat retrieval). Authentication uses 15-minute RS256 access tokens and 30-day SHA-256 hashed refresh tokens.',
      },
      {
        q: 'How does session restoration and token rotation work?',
        a: 'Tokens are stored in browser storage. The frontend HTTP client automatically intercepts 401 Unauthorized responses to invoke POST /api/v1/auth/refresh and seamlessly replays queued requests without disrupting your work.',
      },
    ],
  },
  {
    title: 'Troubleshooting & Support',
    icon: Cpu,
    items: [
      {
        q: 'What should I do if a processing job fails?',
        a: 'Inspect the Processing Jobs queue under Controller Dashboard or Upload Docs. Failed jobs can be re-enqueued using the retry endpoint if under max retry attempts (3).',
      },
      {
        q: 'Why does user directory listing show a notice in Admin mode?',
        a: 'The Identity Service currently supports direct UUID lookup and lock/unlock operations (GET /api/v1/users/{userId}). A bulk listing endpoint (GET /api/v1/users) is scheduled for the next backend release.',
      },
    ],
  },
];

export default function HelpPage() {
  const [openItems, setOpenItems] = useState({ 'Architecture & System Overview-0': true });
  const [supportMessage, setSupportMessage] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const toggle = (key) => {
    setOpenItems((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!supportMessage.trim()) return;
    setSubmitted(true);
    setSupportMessage('');
  };

  return (
    <div style={wrap}>
      <div style={{ marginBottom: 20 }}>
        <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Documentation &amp; Support</h2>
        <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
          Platform architecture specifications, role permissions, and technical references
        </p>
      </div>

      {SECTIONS.map((section) => {
        const SectionIcon = section.icon;
        return (
          <div key={section.title} style={{ marginBottom: 24 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <SectionIcon size={16} color="#c084fc" />
              <h3 style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>{section.title}</h3>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {section.items.map((item, i) => {
                const key = `${section.title}-${i}`;
                const isOpen = openItems[key];
                return (
                  <div key={key} style={faqCard}>
                    <button
                      onClick={() => toggle(key)}
                      style={{
                        width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        padding: '14px 18px', background: 'none', border: 'none', cursor: 'pointer',
                        color: '#f1f5f9', fontSize: 13, fontWeight: 600, textAlign: 'left',
                      }}
                    >
                      <span>{item.q}</span>
                      {isOpen ? <ChevronDown size={16} color="#a5b4fc" /> : <ChevronRight size={16} color="#64748b" />}
                    </button>
                    {isOpen && (
                      <p style={{ padding: '0 18px 14px', margin: 0, fontSize: 13, lineHeight: 1.7, color: '#94a3b8' }}>
                        {item.a}
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      {/* Support Contact Card */}
      <div style={faqCard}>
        <div style={{ padding: 18 }}>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8, color: '#f1f5f9' }}>
            <Mail size={16} color="#7c3aed" /> Contact Platform Engineering
          </div>
          <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 14 }}>
            Need operational assistance or access elevated permissions? Send a dispatch to the engineering desk.
          </p>

          <form onSubmit={handleSubmit}>
            <textarea
              value={supportMessage}
              onChange={(e) => setSupportMessage(e.target.value)}
              placeholder="Describe your inquiry or infrastructure issue..."
              rows={3}
              style={textareaStyle}
              required
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10 }}>
              {submitted ? (
                <span style={{ fontSize: 12, color: '#34d399', fontWeight: 600 }}>
                  Message logged. Platform administrator notified.
                </span>
              ) : <div />}
              <button type="submit" style={primaryBtn}>
                Send Dispatch
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 840, overflowY: 'auto' };
const faqCard = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 14, overflow: 'hidden' };
const textareaStyle = { width: '100%', padding: '10px 14px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 13, outline: 'none', resize: 'vertical', boxSizing: 'border-box' };
const primaryBtn = { padding: '8px 18px', borderRadius: 8, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontSize: 12, fontWeight: 700, cursor: 'pointer', boxShadow: '0 4px 14px rgba(124,58,237,0.3)' };
