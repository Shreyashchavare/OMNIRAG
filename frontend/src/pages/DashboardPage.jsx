// src/pages/DashboardPage.jsx
// Main dashboard: KPI cards, query volume chart, vector DB status,
// recent documents list, and recent query history.
// Props:
//   setPage  → navigate to another page

import {
  FileText, Database, MessageSquare, Zap, Hash,
  Layers, HardDrive, Activity, File, Mic, Video, Image, Clock,
} from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts';
import { documents, queryHistory, weeklyData } from '../data/mockData';

// ─── COMPONENT ────────────────────────────────────────────────────────────────
export default function DashboardPage({ setPage }) {
  return (
    <div style={wrap}>

      {/* ── KPI CARDS ───────────────────────────────────────────────────── */}
      <div style={grid4}>
        <StatCard label="Total Documents" value="7"      change="+2 today"      icon={FileText}      color="#6366f1" />
        <StatCard label="Total Vectors"   value="1,448"  change="+312 today"    icon={Database}      color="#8b5cf6" />
        <StatCard label="Queries Today"   value="241"    change="+18% vs avg"   icon={MessageSquare} color="#ec4899" />
        <StatCard label="Avg Query Time"  value="43 ms"  change="↓ 8ms faster"  icon={Zap}           color="#f59e0b" />
      </div>

      {/* ── CHART + VECTOR STATS ────────────────────────────────────────── */}
      <div style={grid2col}>
        {/* Query volume area chart */}
        <div style={card}>
          <Row>
            <span style={cardTitle}>Query Volume — 7 days</span>
            <span style={faint}>1,074 total</span>
          </Row>
          <ResponsiveContainer width="100%" height={170}>
            <AreaChart data={weeklyData}>
              <defs>
                <linearGradient id="qGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#6366f1" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#6366f1" stopOpacity={0}   />
                </linearGradient>
              </defs>
              <XAxis dataKey="day" tick={tick} axisLine={false} tickLine={false} />
              <YAxis             tick={tick} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={ttStyle} />
              <Area type="monotone" dataKey="queries" stroke="#6366f1" fill="url(#qGrad)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Vector DB status */}
        <div style={card}>
          <span style={{ ...cardTitle, display: 'block', marginBottom: 18 }}>Vector DB Status</span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 13 }}>
            {[
              { k: 'Total Vectors',  v: '1,448',             icon: Hash,      c: '#a5b4fc' },
              { k: 'Dimensions',     v: '1,536',             icon: Layers,    c: '#a5b4fc' },
              { k: 'Index Size',     v: '284 MB',            icon: HardDrive, c: '#a5b4fc' },
              { k: 'Cache Hit Rate', v: '71%',               icon: Zap,       c: '#fbbf24' },
              { k: 'Avg Latency',    v: '43 ms',             icon: Activity,  c: '#34d399' },
            ].map(({ k, v, icon: Icon, c }) => (
              <div key={k} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7, color: '#64748b', fontSize: 13 }}>
                  <Icon size={14} /> {k}
                </div>
                <span style={{ fontSize: 13, fontWeight: 700, color: c }}>{v}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── RECENT DOCS + QUERY HISTORY ─────────────────────────────────── */}
      <div style={grid2col}>
        {/* Recent documents */}
        <div style={card}>
          <Row>
            <span style={cardTitle}>Recent Documents</span>
            <button onClick={() => setPage('documents')} style={linkBtn}>View all</button>
          </Row>
          <div style={{ marginTop: 8 }}>
            {documents.slice(0, 5).map((doc) => (
              <div key={doc.id} style={listRow}>
                <DocIcon type={doc.type} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={docName}>{doc.name}</div>
                  <div style={docMeta}>{doc.chunks} chunks · {doc.date}</div>
                </div>
                <StatusBadge status={doc.status} />
              </div>
            ))}
          </div>
        </div>

        {/* Query history */}
        <div style={card}>
          <Row>
            <span style={cardTitle}>Recent Queries</span>
            <button onClick={() => setPage('chat')} style={linkBtn}>Open Chat</button>
          </Row>
          <div style={{ marginTop: 8 }}>
            {queryHistory.map((q) => (
              <div key={q.id} style={{ ...listRow, alignItems: 'flex-start', cursor: 'default' }}>
                <div style={{ flex: 1 }}>
                  <div style={docName}>{q.query}</div>
                  <div style={{ ...docMeta, display: 'flex', gap: 12 }}>
                    <span><Clock size={10} style={{ verticalAlign: 'middle', marginRight: 2 }} />{q.time}</span>
                    <span>{q.tokens.toLocaleString()} tokens</span>
                    <span>{q.docs} docs</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

    </div>
  );
}

// ─── SUB-COMPONENTS ───────────────────────────────────────────────────────────
function StatCard({ label, value, change, icon: Icon, color }) {
  return (
    <div style={card}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ color: '#64748b', fontSize: 12, marginBottom: 8 }}>{label}</div>
          <div style={{ fontSize: 27, fontWeight: 900, color: '#f1f5f9', letterSpacing: '-0.02em' }}>{value}</div>
          <div style={{ fontSize: 11, color: '#22c55e', marginTop: 4 }}>{change}</div>
        </div>
        <div style={{
          width: 38, height: 38, borderRadius: 11,
          background: `${color}1a`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon size={19} color={color} />
        </div>
      </div>
    </div>
  );
}

function DocIcon({ type }) {
  const map = {
    pdf:   { icon: File,  color: '#ef4444', bg: 'rgba(239,68,68,0.12)'   },
    audio: { icon: Mic,   color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)'  },
    video: { icon: Video, color: '#0369a1', bg: 'rgba(3,105,161,0.12)'   },
    image: { icon: Image, color: '#10b981', bg: 'rgba(16,185,129,0.12)'  },
  };
  const { icon: Icon, color, bg } = map[type] || map.pdf;
  return (
    <div style={{
      width: 30, height: 30, borderRadius: 8, background: bg, flexShrink: 0,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <Icon size={13} color={color} />
    </div>
  );
}

function StatusBadge({ status }) {
  const ok = status === 'Processed';
  return (
    <span style={{
      fontSize: 10, padding: '2px 9px', borderRadius: 99, flexShrink: 0,
      background: ok ? 'rgba(16,185,129,0.12)' : 'rgba(251,191,36,0.12)',
      color:      ok ? '#10b981'                : '#fbbf24',
    }}>
      {status}
    </span>
  );
}

function Row({ children }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
      {children}
    </div>
  );
}

// ─── STYLES ───────────────────────────────────────────────────────────────────
const wrap    = { padding: '24px 28px', maxWidth: 1200, display: 'flex', flexDirection: 'column', gap: 18 };
const grid4   = { display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14 };
const grid2col= { display: 'grid', gridTemplateColumns: '1.6fr 1fr',    gap: 18 };

const card    = {
  background: 'rgba(255,255,255,0.03)',
  border:     '1px solid rgba(255,255,255,0.07)',
  borderRadius: 16, padding: '18px 20px',
};

const cardTitle = { fontWeight: 700, fontSize: 14 };
const faint     = { fontSize: 12, color: '#64748b' };

const listRow = {
  display: 'flex', alignItems: 'center', gap: 10,
  padding: '9px 0',
  borderBottom: '1px solid rgba(255,255,255,0.04)',
};
const docName = { fontSize: 12, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#cbd5e1' };
const docMeta = { fontSize: 11, color: '#475569', marginTop: 2 };

const linkBtn = {
  fontSize: 12, color: '#a5b4fc',
  background: 'none', border: 'none', cursor: 'pointer',
};

const tick    = { fill: '#475569', fontSize: 11 };
const ttStyle = {
  background: '#1e1e2e', border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 8, color: '#e2e8f0', fontSize: 12,
};
