// src/pages/ChatPage.jsx
// ChatGPT-style chat UI with:
//   - Left panel: active document context list
//   - Message bubbles (user + AI), bullet points, citation chips
//   - Typing animation (3-dot bounce)
//   - Enter to send; dummy AI responses cycle through 3 templates
// Props: none (fully self-contained state)

import { useState, useRef, useEffect } from 'react';
import { Bot, User, Send, Paperclip, Mic2, File, Image, Mic, Link2, Sparkles } from 'lucide-react';
import { documents, initChatMessages } from '../data/mockData';

// ─── AI RESPONSE TEMPLATES ────────────────────────────────────────────────────
const AI_TEMPLATES = [
  {
    content: 'Based on the indexed documents, I found the following:',
    bullets: [
      'The system uses a microservices architecture with 12 independent services',
      'API gateway handles authentication, rate limiting, and intelligent routing',
      'Vector storage auto-scales based on embedding volume and query load',
    ],
    citations: [
      { doc: 'Tech_Architecture.pdf',    page: 3 },
      { doc: 'Product_Roadmap_2025.pdf', page: 8 },
    ],
  },
  {
    content: 'Across the uploaded knowledge base, here are the relevant findings:',
    bullets: [
      'Customer satisfaction score rose to 87% after the Q3 UX redesign',
      'Top pain point: onboarding took an average of 4.2 days — reduced to 1.8 days',
      '92% of enterprise customers rated the new dashboard as "excellent"',
    ],
    citations: [
      { doc: 'Customer_Survey.pdf', page: 5 },
      { doc: 'Customer_Survey.pdf', page: 11 },
    ],
  },
  {
    content: 'The Q4 Financial Report and Roadmap documents together reveal:',
    bullets: [
      'Revenue target for Q1 2026 is set at $52M, implying 10% QoQ growth',
      'Three new product lines are planned: Analytics+, API Hub, and RAG Studio',
      'Headcount is expected to grow by 40 engineers across ML and Platform teams',
    ],
    citations: [
      { doc: 'Q4_Financial_Report.pdf',  page: 18 },
      { doc: 'Product_Roadmap_2025.pdf', page: 14 },
    ],
  },
];

let templateCursor = 0;
const nextTemplate = () => {
  const t = AI_TEMPLATES[templateCursor % AI_TEMPLATES.length];
  templateCursor += 1;
  return t;
};

// ─── COMPONENT ────────────────────────────────────────────────────────────────
export default function ChatPage() {
  const [messages,  setMessages]  = useState(initChatMessages);
  const [input,     setInput]     = useState('');
  const [isTyping,  setIsTyping]  = useState(false);
  const chatEndRef = useRef(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  const sendMessage = () => {
    if (!input.trim()) return;

    const userMsg = {
      role: 'user',
      content: input,
      time: now(),
    };
    setMessages((p) => [...p, userMsg]);
    setInput('');
    setIsTyping(true);

    setTimeout(() => {
      setIsTyping(false);
      const tpl = nextTemplate();
      setMessages((p) => [...p, {
        role: 'assistant',
        ...tpl,
        time: now(),
      }]);
    }, 2000 + Math.random() * 800);
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>

      {/* ── DOC CONTEXT PANEL ───────────────────────────────────────────── */}
      <aside style={contextPanel}>
        <div style={contextLabel}>Active Context</div>
        {documents.filter((d) => d.status === 'Processed').map((doc) => (
          <div key={doc.id} style={docChip}>
            <div style={docChipIcon(doc.type)}>
              <DocTypeIcon type={doc.type} size={12} />
            </div>
            <span style={docChipName}>{doc.name}</span>
          </div>
        ))}

        <div style={{ marginTop: 20, padding: '12px', borderRadius: 10, background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.12)' }}>
          <div style={{ fontSize: 11, color: '#a5b4fc', fontWeight: 700, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 5 }}>
            <Sparkles size={11} /> Vector Context
          </div>
          <div style={{ fontSize: 11, color: '#64748b', lineHeight: 1.6 }}>
            6 docs · 1,381 chunks<br />
            Top-k: 5 · Model: GPT-4o
          </div>
        </div>
      </aside>

      {/* ── CHAT AREA ───────────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

        {/* Message list */}
        <div style={messageList}>
          {messages.map((msg, i) => (
            <MessageBubble key={i} msg={msg} />
          ))}

          {/* Typing indicator */}
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
                        background: '#6366f1', display: 'inline-block',
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

        {/* Input bar */}
        <div style={inputWrap}>
          <div style={inputBox}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
              placeholder="Ask anything about your documents..."
              style={inputField}
            />
            <Paperclip size={15} color="#475569" style={{ cursor: 'pointer' }} />
            <Mic2      size={15} color="#475569" style={{ cursor: 'pointer' }} />
          </div>
          <button onClick={sendMessage} style={sendBtn}>
            <Send size={16} color="#fff" />
          </button>
        </div>

      </div>
    </div>
  );
}

// ─── MESSAGE BUBBLE ───────────────────────────────────────────────────────────
function MessageBubble({ msg }) {
  const isUser = msg.role === 'user';
  return (
    <div style={{
      display: 'flex', gap: 12,
      flexDirection: isUser ? 'row-reverse' : 'row',
      alignItems: 'flex-start',
      animation: 'fadeIn 0.3s ease forwards',
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
                  <Link2 size={9} color="#a5b4fc" />
                  {c.doc} · p.{c.page}
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{
          fontSize: 10, color: '#475569', marginTop: 3,
          textAlign: isUser ? 'right' : 'left',
        }}>{msg.time}</div>
      </div>
    </div>
  );
}

// ─── AVATAR ───────────────────────────────────────────────────────────────────
function Avatar({ isAI }) {
  return (
    <div style={{
      width: 30, height: 30, borderRadius: '50%', flexShrink: 0,
      background: isAI ? 'rgba(255,255,255,0.06)' : 'linear-gradient(135deg,#6366f1,#a855f7)',
      border: '1px solid rgba(255,255,255,0.1)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {isAI ? <Bot size={14} color="#a5b4fc" /> : <User size={14} color="#fff" />}
    </div>
  );
}

// ─── DOC TYPE ICON ────────────────────────────────────────────────────────────
function DocTypeIcon({ type, size = 13 }) {
  if (type === 'pdf')   return <File  size={size} color="#ef4444" />;
  if (type === 'audio') return <Mic   size={size} color="#8b5cf6" />;
  if (type === 'video') return <File  size={size} color="#0369a1" />;
  return <Image size={size} color="#10b981" />;
}

// ─── HELPERS ──────────────────────────────────────────────────────────────────
const now = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

// ─── STYLES ───────────────────────────────────────────────────────────────────
const contextPanel = {
  width: 224, flexShrink: 0,
  borderRight: '1px solid rgba(255,255,255,0.06)',
  padding: 14, overflowY: 'auto',
  display: 'flex', flexDirection: 'column', gap: 5,
};

const contextLabel = {
  fontSize: 10, fontWeight: 700, color: '#64748b',
  textTransform: 'uppercase', letterSpacing: '0.08em',
  marginBottom: 6, padding: '0 4px',
};

const docChip = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '7px 9px', borderRadius: 9, cursor: 'pointer',
  background: 'rgba(255,255,255,0.03)',
  border: '1px solid rgba(255,255,255,0.06)',
};

const docChipIcon = (type) => ({
  width: 26, height: 26, borderRadius: 6, flexShrink: 0,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  background: type === 'pdf' ? 'rgba(239,68,68,0.12)' : type === 'audio' ? 'rgba(139,92,246,0.12)' : type === 'video' ? 'rgba(3,105,161,0.12)' : 'rgba(16,185,129,0.12)',
});

const docChipName = {
  fontSize: 10, overflow: 'hidden',
  textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#94a3b8',
};

const messageList = {
  flex: 1, overflowY: 'auto', padding: '20px 28px',
  display: 'flex', flexDirection: 'column', gap: 20,
};

const inputWrap = {
  padding: '14px 28px', borderTop: '1px solid rgba(255,255,255,0.06)',
  display: 'flex', gap: 10, alignItems: 'center',
};

const inputBox = {
  flex: 1, background: 'rgba(255,255,255,0.04)',
  border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 12, padding: '10px 14px',
  display: 'flex', gap: 10, alignItems: 'center',
};

const inputField = {
  flex: 1, background: 'none', border: 'none', outline: 'none',
  color: '#e2e8f0', fontSize: 13,
};

const sendBtn = {
  width: 42, height: 42, borderRadius: 11, border: 'none', flexShrink: 0,
  background: 'linear-gradient(135deg,#6366f1,#a855f7)',
  cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
};

const userBubble = {
  background: 'rgba(99,102,241,0.15)',
  border: '1px solid rgba(99,102,241,0.25)',
  borderRadius: '18px 4px 18px 18px',
  padding: '12px 16px', color: '#e2e8f0',
};

const aiBubble = {
  background: 'rgba(255,255,255,0.04)',
  border: '1px solid rgba(255,255,255,0.07)',
  borderRadius: '4px 18px 18px 18px',
  padding: '12px 16px', color: '#e2e8f0',
};

const citationChip = {
  display: 'flex', alignItems: 'center', gap: 5,
  background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)',
  borderRadius: 7, padding: '3px 9px',
  fontSize: 10, color: '#a5b4fc', cursor: 'pointer',
};
