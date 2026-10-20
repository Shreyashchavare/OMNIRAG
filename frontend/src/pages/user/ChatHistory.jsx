import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, MessageSquare, ArrowRight, Clock } from 'lucide-react';
import { chatHistoryData } from '../../data/mockData';

export default function ChatHistory() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [history, setHistory] = useState(chatHistoryData);

  const filtered = history.filter((c) =>
    c.title.toLowerCase().includes(search.toLowerCase()) ||
    c.preview.toLowerCase().includes(search.toLowerCase())
  );

  const handleContinue = (conversation) => {
    navigate('/user/chat', { state: { resumeTitle: conversation.title } });
  };

  const handleClear = () => {
    setHistory([]);
  };

  return (
    <div style={wrap}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Conversation History</h2>
          <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
            Past retrieval-augmented generation search sessions and cited answers
          </p>
        </div>
        {history.length > 0 && (
          <button onClick={handleClear} style={dangerBtn}>
            Clear History
          </button>
        )}
      </div>

      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, marginBottom: 18,
        background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 10, padding: '8px 14px', maxWidth: 400,
      }}>
        <Search size={14} color="#64748b" />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filter conversations by keyword..."
          style={{ flex: 1, background: 'none', border: 'none', outline: 'none', fontSize: 13, color: '#f1f5f9' }}
        />
      </div>

      {filtered.length === 0 ? (
        <div style={{ padding: '36px 0', textAlign: 'center', color: '#64748b', fontSize: 13 }}>
          No conversation history found matching your filter.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {filtered.map((c) => (
            <div
              key={c.id}
              onClick={() => handleContinue(c)}
              style={historyCard}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <MessageSquare size={15} color="#c084fc" />
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>{c.title}</span>
                </div>
                <span style={{ fontSize: 11, color: '#64748b', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Clock size={11} /> {c.date}
                </span>
              </div>

              <p style={{ fontSize: 12, color: '#94a3b8', margin: '8px 0 10px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {c.preview}
              </p>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid rgba(255,255,255,0.05)', paddingTop: 8 }}>
                <span style={{ fontSize: 11, color: '#64748b' }}>{c.messages} messages exchanged</span>
                <span style={{ fontSize: 12, color: '#a5b4fc', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
                  Resume Conversation <ArrowRight size={13} />
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 840, overflowY: 'auto' };
const historyCard = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 14, padding: '16px 18px', cursor: 'pointer', transition: 'border-color 0.15s' };
const dangerBtn = { padding: '7px 14px', borderRadius: 8, border: '1px solid rgba(239,68,68,0.3)', background: 'rgba(239,68,68,0.1)', color: '#ef4444', fontWeight: 600, fontSize: 12, cursor: 'pointer' };
