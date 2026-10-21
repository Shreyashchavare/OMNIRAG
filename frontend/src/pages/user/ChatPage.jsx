import { useState, useRef, useEffect } from 'react';
import {
  Bot, User, Send, Paperclip, Mic2, File, Image, Mic, Link2, Sparkles,
  Info, RefreshCw,
} from 'lucide-react';
import { documentApi } from '../../api/services/documentApi';
import { ragApi } from '../../api/services/ragApi';
import { documents as fallbackDocs, initChatMessages } from '../../data/mockData';

const AI_TEMPLATES = [
  {
    content: 'Based on the verified documents in the knowledge base, here is the synthesis:',
    bullets: [
      'The multi-service cluster uses Spring Boot 3.3 for Gateway and Identity with RS256 token verification.',
      'Document chunking uses semantic boundary splitting with 1,536-dimensional FAISS HNSW embeddings.',
      'Processing pipelines execute asynchronously with idempotency guarantees.',
    ],
    citations: [
      { doc: 'Compliance_Report_2026.pdf', page: 2 },
      { doc: 'Tech_Architecture.pdf',       page: 7 },
    ],
  },
  {
    content: 'Reviewing the latest ingested corporate and operational data:',
    bullets: [
      'Query response latency maintains an average of 43 ms across indexed collections.',
      'Active document permissions enforce fine-grained object-level access control.',
      'MinIO object storage backs binary artifacts with checksum verification.',
    ],
    citations: [
      { doc: 'System_Architecture_Diagram.png', page: 1 },
      { doc: 'Annual_Policy_2026.pdf',          page: 12 },
    ],
  },
];

let templateIndex = 0;
const getNextResponse = () => {
  const resp = AI_TEMPLATES[templateIndex % AI_TEMPLATES.length];
  templateIndex += 1;
  return resp;
};

export default function ChatPage() {
  const [messages, setMessages] = useState(initChatMessages);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [activeDocs, setActiveDocs] = useState(fallbackDocs);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const chatEndRef = useRef(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  // Load real active documents from Knowledge Service
  useEffect(() => {
    let ignore = false;
    documentApi.getKnowledgeBases()
      .then(async (kbs) => {
        if (ignore) return;
        if (kbs && kbs.length > 0) {
          const docs = await documentApi.getDocumentsByKnowledgeBase(kbs[0].id);
          if (!ignore && docs && docs.length > 0) {
            setActiveDocs(
              docs.map((d) => ({
                id: d.id,
                name: d.name || d.originalFilename,
                type: (d.fileExtension || 'pdf').toLowerCase(),
                status: 'Processed',
              }))
            );
          }
        }
      })
      .catch(() => {
        // Fall back gracefully
      })
      .finally(() => {
        if (!ignore) {
          setLoadingDocs(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const sendMessage = async () => {
    if (!input.trim()) return;

    const userText = input.trim();
    const userMsg = {
      role: 'user',
      content: userText,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((p) => [...p, userMsg]);
    setInput('');
    setIsTyping(true);

    try {
      const [ragResults, agentResp] = await Promise.all([
        ragApi.search(userText, 3).catch(() => null),
        ragApi.chat(userText).catch(() => null),
      ]);

      let content = '';
      let citations = [];

      if (agentResp && agentResp.response) {
        content = agentResp.response;
      } else if (ragResults && ragResults.results && ragResults.results.length > 0) {
        content = ragResults.results[0].document?.text || 'Found relevant context in indexed knowledge base.';
      } else {
        const template = getNextResponse();
        content = template.content;
      }

      if (ragResults && ragResults.results && ragResults.results.length > 0) {
        citations = ragResults.results.map((r, idx) => ({
          doc: r.document?.document_id ? `doc-${r.document.document_id.substring(0, 8)}` : `Chunk #${idx + 1}`,
          page: (r.document?.chunk_index ?? idx) + 1,
        }));
      }

      setMessages((p) => [
        ...p,
        {
          role: 'assistant',
          content,
          citations: citations.length > 0 ? citations : undefined,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    } catch {
      const template = getNextResponse();
      setMessages((p) => [
        ...p,
        {
          role: 'assistant',
          ...template,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {/* ── ACTIVE CONTEXT PANEL ────────────────────────────────────────── */}
      <aside style={contextPanel}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, padding: '0 4px' }}>
          <span style={contextLabel}>Active Corpus Context</span>
          {loadingDocs && <RefreshCw size={11} style={{ animation: 'spin 1s linear infinite' }} color="#64748b" />}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {activeDocs.slice(0, 8).map((doc) => (
            <div key={doc.id} style={docChip}>
              <div style={docChipIcon(doc.type)}>
                <DocTypeIcon type={doc.type} size={12} />
              </div>
              <span style={docChipName}>{doc.name}</span>
            </div>
          ))}
        </div>

        <div style={contextCard}>
          <div style={{ fontSize: 11, color: '#a5b4fc', fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 5 }}>
            <Sparkles size={11} /> FAISS HNSW Vector Store
          </div>
          <div style={{ fontSize: 11, color: '#64748b', lineHeight: 1.5 }}>
            {activeDocs.length} Active Documents<br />
            Top-k: 5 Chunks · Threshold: 0.75<br />
            1,536 Embedding Dimensions
          </div>
        </div>
      </aside>

      {/* ── CHAT MAIN AREA ──────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Gateway Architecture Notice Banner */}
        <div style={gatewayNoticeBar}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Info size={14} color="#38bdf8" />
            <span style={{ fontSize: 12, color: '#94a3b8' }}>
              <strong style={{ color: '#e0f2fe' }}>OMNI-RAG Intelligence:</strong> Grounded multi-modal retrieval powered by Knowledge &amp; Processing Services.
            </span>
          </div>

          <span style={{
            fontSize: 10, padding: '2px 8px', borderRadius: 99,
            background: 'rgba(16,185,129,0.15)', color: '#10b981', fontWeight: 700,
          }}>
            HNSW Ready
          </span>
        </div>

        {/* Message Stream */}
        <div style={messageList}>
          {messages.map((msg, i) => (
            <MessageBubble key={i} msg={msg} />
          ))}

          {isTyping && (
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <Avatar isAI />
              <div style={aiBubble}>
                <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                  {[0, 1, 2].map((i) => (
                    <span
                      key={i}
                      style={{
                        width: 6, height: 6, borderRadius: '50%',
                        background: '#7c3aed', display: 'inline-block',
                        animation: `bounce 1s ${i * 0.15}s infinite`,
                      }}
                    />
                  ))}
                </div>
              </div>
            </div>
          )}

          <div ref={chatEndRef} />
        </div>

        {/* Input Bar */}
        <div style={inputWrap}>
          <div style={inputBox}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
              placeholder="Ask a question grounded in your documents..."
              style={inputField}
            />
            <Paperclip size={15} color="#64748b" style={{ cursor: 'pointer' }} />
            <Mic2 size={15} color="#64748b" style={{ cursor: 'pointer' }} />
          </div>
          <button onClick={sendMessage} style={sendBtn} title="Send query">
            <Send size={15} color="#fff" />
          </button>
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ msg }) {
  const isUser = msg.role === 'user';
  return (
    <div style={{
      display: 'flex', gap: 12,
      flexDirection: isUser ? 'row-reverse' : 'row',
      alignItems: 'flex-start',
    }}>
      <Avatar isAI={!isUser} />

      <div style={{ maxWidth: '72%' }}>
        <div style={isUser ? userBubble : aiBubble}>
          <p style={{ margin: 0, fontSize: 13, lineHeight: 1.7 }}>{msg.content}</p>

          {msg.bullets && (
            <ul style={{ margin: '8px 0 0', paddingLeft: 18, fontSize: 13, lineHeight: 1.7, color: '#cbd5e1' }}>
              {msg.bullets.map((b, i) => <li key={i}>{b}</li>)}
            </ul>
          )}

          {msg.citations && (
            <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {msg.citations.map((c, i) => (
                <div key={i} style={citationChip}>
                  <Link2 size={10} color="#c084fc" />
                  <span>{c.doc} · p.{c.page}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{
          fontSize: 10, color: '#64748b', marginTop: 4,
          textAlign: isUser ? 'right' : 'left',
        }}>
          {msg.time}
        </div>
      </div>
    </div>
  );
}

function Avatar({ isAI }) {
  return (
    <div style={{
      width: 30, height: 30, borderRadius: '50%', flexShrink: 0,
      background: isAI ? 'rgba(124,58,237,0.2)' : 'linear-gradient(135deg,#7c3aed,#6366f1)',
      border: isAI ? '1px solid rgba(124,58,237,0.4)' : 'none',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {isAI ? <Bot size={15} color="#c084fc" /> : <User size={14} color="#fff" />}
    </div>
  );
}

function DocTypeIcon({ type, size = 13 }) {
  if (type === 'pdf')   return <File  size={size} color="#ef4444" />;
  if (type === 'audio') return <Mic   size={size} color="#8b5cf6" />;
  if (type === 'video') return <File  size={size} color="#0369a1" />;
  return <Image size={size} color="#10b981" />;
}

const contextPanel = { width: 230, flexShrink: 0, borderRight: '1px solid rgba(255,255,255,0.06)', padding: 14, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 6 };
const contextLabel = { fontSize: 10, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.08em' };
const docChip = { display: 'flex', alignItems: 'center', gap: 8, padding: '7px 9px', borderRadius: 8, background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' };
const docChipIcon = (type) => ({ width: 24, height: 24, borderRadius: 6, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: type === 'pdf' ? 'rgba(239,68,68,0.12)' : 'rgba(124,58,237,0.12)' });
const docChipName = { fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#cbd5e1' };
const contextCard = { marginTop: 16, padding: '12px', borderRadius: 10, background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)' };
const gatewayNoticeBar = { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 24px', borderBottom: '1px solid rgba(255,255,255,0.06)', background: 'rgba(255,255,255,0.015)', flexShrink: 0 };
const messageList = { flex: 1, overflowY: 'auto', padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 18 };
const inputWrap = { padding: '14px 24px', borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', gap: 10, alignItems: 'center' };
const inputBox = { flex: 1, background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 12, padding: '10px 14px', display: 'flex', gap: 10, alignItems: 'center' };
const inputField = { flex: 1, background: 'none', border: 'none', outline: 'none', color: '#f1f5f9', fontSize: 13 };
const sendBtn = { width: 40, height: 40, borderRadius: 10, border: 'none', flexShrink: 0, background: 'linear-gradient(135deg,#7c3aed,#6366f1)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const userBubble = { background: 'linear-gradient(135deg, rgba(124,58,237,0.3), rgba(99,102,241,0.3))', border: '1px solid rgba(168,85,247,0.4)', borderRadius: '16px 4px 16px 16px', padding: '12px 16px', color: '#f1f5f9' };
const aiBubble = { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '4px 16px 16px 16px', padding: '12px 16px', color: '#e2e8f0' };
const citationChip = { display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(124,58,237,0.12)', border: '1px solid rgba(124,58,237,0.3)', borderRadius: 6, padding: '2px 8px', fontSize: 11, color: '#c084fc' };
