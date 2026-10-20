// src/pages/AnalyticsPage.jsx
// Full analytics dashboard:
//   - 6 KPI cards with trend badges
//   - Daily query volume bar chart
//   - Token usage area chart
//   - Document type donut chart
//   - System health progress bars
// Props: none

import {
  FileText, Database, HardDrive, Users, Zap, Brain,
} from 'lucide-react';
import {
  BarChart, Bar, AreaChart, Area, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts';
import { weeklyData, docTypeData } from '../data/mockData';

// ─── COMPONENT ────────────────────────────────────────────────────────────────
export default function AnalyticsPage() {
  return (
    <div style={wrap}>

      {/* ── KPI CARDS ───────────────────────────────────────────────────── */}
      <div style={kpiGrid}>
        <KpiCard label="Total Documents" value="7"      icon={FileText}  color="#6366f1" trend="+2"    />
        <KpiCard label="Embeddings"      value="1,448"  icon={Database}  color="#8b5cf6" trend="+312"  />
        <KpiCard label="Vector DB Size"  value="284 MB" icon={HardDrive} color="#ec4899" trend="+42MB" />
        <KpiCard label="Active Users"    value="3"      icon={Users}     color="#10b981" trend="+1"    />
        <KpiCard label="Cache Hits"      value="71%"    icon={Zap}       color="#f59e0b" trend="+4%"   />
        <KpiCard label="LLM Requests"    value="1,074"  icon={Brain}     color="#3b82f6" trend="+241"  />
      </div>

      {/* ── CHARTS ROW 1 ────────────────────────────────────────────────── */}
      <div style={grid2}>

        {/* Bar chart — query volume */}
        <div style={card}>
          <div style={cardTitle}>Daily Query Volume</div>
          <ResponsiveContainer width="100%" height={190}>
            <BarChart data={weeklyData} barSize={18}>
              <XAxis dataKey="day" tick={tick} axisLine={false} tickLine={false} />
              <YAxis             tick={tick} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={ttStyle} />
              <Bar dataKey="queries" fill="#6366f1" radius={[5, 5, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Area chart — token usage */}
        <div style={card}>
          <div style={cardTitle}>Token Usage (K / day)</div>
          <ResponsiveContainer width="100%" height={190}>
            <AreaChart data={weeklyData}>
              <defs>
                <linearGradient id="tGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#ec4899" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#ec4899" stopOpacity={0}   />
                </linearGradient>
              </defs>
              <XAxis dataKey="day" tick={tick} axisLine={false} tickLine={false} />
              <YAxis             tick={tick} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={ttStyle} />
              <Area type="monotone" dataKey="tokens" stroke="#ec4899" fill="url(#tGrad)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* ── CHARTS ROW 2 ────────────────────────────────────────────────── */}
      <div style={grid2b}>

        {/* Donut — doc types */}
        <div style={card}>
          <div style={cardTitle}>Document Types</div>
          <ResponsiveContainer width="100%" height={170}>
            <PieChart>
              <Pie
                data={docTypeData}
                cx="50%" cy="50%"
                innerRadius={48} outerRadius={74}
                dataKey="value" stroke="none"
              >
                {docTypeData.map((entry, i) => (
                  <Cell key={i} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip contentStyle={ttStyle} />
            </PieChart>
          </ResponsiveContainer>
          <div style={legend}>
            {docTypeData.map((d) => (
              <div key={d.name} style={legendItem}>
                <span style={{ ...dot, background: d.color }} />
                {d.name} {d.value}%
              </div>
            ))}
          </div>
        </div>

        {/* System health */}
        <div style={card}>
          <div style={{ ...cardTitle, marginBottom: 20 }}>System Health</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {HEALTH_METRICS.map(({ label, value, unit, max, color }) => (
              <HealthBar key={label} label={label} value={value} unit={unit} max={max} color={color} />
            ))}
          </div>
        </div>
      </div>

      {/* ── EXTRA STATS TABLE ───────────────────────────────────────────── */}
      <div style={card}>
        <div style={{ ...cardTitle, marginBottom: 16 }}>Processing Summary — Last 7 Days</div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              {['Day', 'Queries', 'Tokens (K)', 'Docs Uploaded', 'Cache Hits', 'Avg Latency'].map((h) => (
                <th key={h} style={thStyle}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {TABLE_DATA.map((row, i) => (
              <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.015)' }}>
                {Object.values(row).map((v, j) => (
                  <td key={j} style={tdStyle}>{v}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </div>
  );
}

// ─── KPI CARD ─────────────────────────────────────────────────────────────────
function KpiCard({ label, value, icon: Icon, color, trend }) {
  return (
    <div style={card}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
        <div style={{ width: 34, height: 34, borderRadius: 10, background: `${color}1a`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon size={17} color={color} />
        </div>
        <span style={{
          fontSize: 10, padding: '2px 8px', borderRadius: 6, fontWeight: 700,
          background: 'rgba(16,185,129,0.1)', color: '#10b981',
        }}>{trend}</span>
      </div>
      <div style={{ fontSize: 22, fontWeight: 900, color: '#f1f5f9', letterSpacing: '-0.02em', marginBottom: 3 }}>
        {value}
      </div>
      <div style={{ fontSize: 12, color: '#64748b' }}>{label}</div>
    </div>
  );
}

// ─── HEALTH BAR ───────────────────────────────────────────────────────────────
function HealthBar({ label, value, unit, max, color }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 5 }}>
        <span style={{ color: '#94a3b8' }}>{label}</span>
        <span style={{ color, fontWeight: 700 }}>{value}{unit}</span>
      </div>
      <div style={{ height: 5, background: 'rgba(255,255,255,0.06)', borderRadius: 99, overflow: 'hidden' }}>
        <div style={{ height: '100%', borderRadius: 99, width: `${(value / max) * 100}%`, background: color }} />
      </div>
    </div>
  );
}

// ─── DATA ─────────────────────────────────────────────────────────────────────
const HEALTH_METRICS = [
  { label: 'API Response Time',      value: 43,  unit: 'ms', max: 200, color: '#10b981' },
  { label: 'Embedding Generation',   value: 128, unit: 'ms', max: 500, color: '#6366f1' },
  { label: 'Vector Query Latency',   value: 23,  unit: 'ms', max: 100, color: '#3b82f6' },
  { label: 'Cache Hit Rate',         value: 71,  unit: '%',  max: 100, color: '#f59e0b' },
  { label: 'Vector DB CPU Usage',    value: 34,  unit: '%',  max: 100, color: '#ec4899' },
];

const TABLE_DATA = [
  { day: 'Mon', queries: 124, tokens: 48,  docs: 3, cache: '68%', latency: '45 ms' },
  { day: 'Tue', queries: 189, tokens: 72,  docs: 5, cache: '70%', latency: '42 ms' },
  { day: 'Wed', queries: 203, tokens: 84,  docs: 4, cache: '73%', latency: '39 ms' },
  { day: 'Thu', queries: 167, tokens: 61,  docs: 7, cache: '69%', latency: '44 ms' },
  { day: 'Fri', queries: 241, tokens: 97,  docs: 9, cache: '75%', latency: '41 ms' },
  { day: 'Sat', queries: 88,  tokens: 34,  docs: 2, cache: '71%', latency: '47 ms' },
  { day: 'Sun', queries: 62,  tokens: 24,  docs: 1, cache: '66%', latency: '50 ms' },
];

// ─── STYLES ───────────────────────────────────────────────────────────────────
const wrap   = { padding: '24px 28px', maxWidth: 1200, display: 'flex', flexDirection: 'column', gap: 18 };
const kpiGrid= { display: 'grid', gridTemplateColumns: 'repeat(6,1fr)', gap: 12 };
const grid2  = { display: 'grid', gridTemplateColumns: '1fr 1fr',       gap: 18 };
const grid2b = { display: 'grid', gridTemplateColumns: '1fr 1.6fr',     gap: 18 };

const card = {
  background: 'rgba(255,255,255,0.03)',
  border:     '1px solid rgba(255,255,255,0.07)',
  borderRadius: 16, padding: '18px 20px',
};

const cardTitle = { fontWeight: 700, fontSize: 14, marginBottom: 16 };

const legend     = { display: 'flex', gap: 14, flexWrap: 'wrap', justifyContent: 'center', marginTop: 8 };
const legendItem = { display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: '#94a3b8' };
const dot        = { width: 8, height: 8, borderRadius: 2, display: 'inline-block' };

const thStyle = {
  textAlign: 'left', padding: '8px 12px',
  color: '#64748b', fontSize: 12, fontWeight: 600,
  borderBottom: '1px solid rgba(255,255,255,0.06)',
};
const tdStyle = {
  padding: '9px 12px', color: '#cbd5e1', fontSize: 12,
  borderBottom: '1px solid rgba(255,255,255,0.03)',
};

const tick    = { fill: '#475569', fontSize: 11 };
const ttStyle = {
  background: '#1e1e2e', border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 8, color: '#e2e8f0', fontSize: 12,
};
