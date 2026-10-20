import { useNavigate } from 'react-router-dom';
import { Play, ArrowRight } from 'lucide-react';
import { models } from '../../data/mockData';

const REQUEST_LOG = [
  { endpoint: '/v1/chat/completions', model: 'llama3-8b-instruct', status: 200, latency: '234ms', time: '10:42:01' },
  { endpoint: '/v1/embeddings',       model: 'mistral-7b-v0.2',    status: 200, latency: '38ms',  time: '10:41:58' },
  { endpoint: '/v1/chat/completions', model: 'llama3-8b-instruct', status: 200, latency: '198ms', time: '10:41:44' },
  { endpoint: '/v1/chat/completions', model: 'llama3-8b-instruct', status: 503, latency: '5000ms',time: '10:40:12' },
  { endpoint: '/v1/embeddings',       model: 'mistral-7b-v0.2',    status: 200, latency: '42ms',  time: '10:39:55' },
];

const RESOURCES = [
  { label: 'Inference Accelerator VRAM', value: 72, max: 100, display: '8.6 / 12 GB', color: '#7c3aed' },
  { label: 'Embedding Worker CPU',      value: 34, max: 100, display: '34% Utilized', color: '#6366f1' },
  { label: 'Redis Vector Cache',        value: 58, max: 100, display: '4.6 / 8 GB',   color: '#f59e0b' },
  { label: 'Host System RAM',           value: 44, max: 100, display: '14.1 / 32 GB', color: '#10b981' },
];

export default function ModelDashboard() {
  const navigate = useNavigate();

  return (
    <div style={wrap}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Model Dashboard &amp; Telemetry</h2>
          <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
            Deployed LLMs, embedding generators, and compute resource allocation
          </p>
        </div>

        <button
          onClick={() => navigate('/controller/testing')}
          style={primaryBtn}
        >
          <Play size={14} fill="#fff" /> Open Testing Sandbox <ArrowRight size={14} />
        </button>
      </div>

      {/* Model Cards */}
      <div>
        <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'block', marginBottom: 12 }}>
          Deployed Inference Engines
        </span>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 14 }}>
          {models.map((m) => (
            <div key={m.name} style={card}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                <div>
                  <div style={{ fontWeight: 800, fontSize: 14, color: '#f1f5f9' }}>{m.name}</div>
                  <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 6, background: 'rgba(255,255,255,0.06)', color: '#94a3b8', marginTop: 4, display: 'inline-block' }}>
                    {m.provider}
                  </span>
                </div>
                <span style={{
                  width: 8, height: 8, borderRadius: '50%',
                  background: m.status === 'Running' ? '#10b981' : '#ef4444',
                  boxShadow: m.status === 'Running' ? '0 0 8px rgba(16,185,129,0.5)' : 'none',
                }} />
              </div>

              <div style={{ fontSize: 12, color: '#64748b', display: 'flex', flexDirection: 'column', gap: 5, marginBottom: 14 }}>
                <span style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>VRAM Allocated:</span> <strong style={{ color: '#cbd5e1' }}>{m.ram}</strong>
                </span>
                <span style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Avg Latency:</span> <strong style={{ color: '#a5b4fc' }}>{m.latency}</strong>
                </span>
                <span style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Requests Today:</span> <strong style={{ color: '#cbd5e1' }}>{m.requests}</strong>
                </span>
              </div>

              <div style={{ display: 'flex', gap: 8, borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: 10 }}>
                <button
                  onClick={() => navigate('/controller/testing')}
                  style={{ ...ghostBtn, flex: 1, color: '#c084fc', borderColor: 'rgba(168,85,247,0.3)' }}
                >
                  Test Model
                </button>
                <button style={{ ...ghostBtn, flex: 1 }}>
                  {m.status === 'Running' ? 'Active' : 'Start'}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Compute Resources */}
      <div style={card}>
        <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 16, color: '#f1f5f9' }}>
          Hardware Resources &amp; Memory Allocation
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
          {RESOURCES.map((r) => (
            <div key={r.label}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 6 }}>
                <span style={{ color: '#94a3b8' }}>{r.label}</span>
                <span style={{ color: r.color, fontWeight: 700 }}>{r.display}</span>
              </div>
              <div style={{ height: 6, background: 'rgba(255,255,255,0.06)', borderRadius: 99, overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${r.value}%`, background: r.color, borderRadius: 99 }} />
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Telemetry Log */}
      <div style={card}>
        <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 12, color: '#f1f5f9' }}>
          Recent Ingestion &amp; Search Route Activity
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              {['Endpoint', 'Model', 'Status', 'Latency', 'Time'].map((h) => (
                <th key={h} style={thStyle}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {REQUEST_LOG.map((row, i) => (
              <tr key={i}>
                <td style={tdStyle}><code>{row.endpoint}</code></td>
                <td style={tdStyle}>{row.model}</td>
                <td style={tdStyle}>
                  <span style={{
                    fontSize: 10, padding: '2px 8px', borderRadius: 99, fontWeight: 700,
                    background: row.status === 200 ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                    color: row.status === 200 ? '#10b981' : '#ef4444',
                  }}>
                    {row.status}
                  </span>
                </td>
                <td style={tdStyle}>{row.latency}</td>
                <td style={tdStyle}>{row.time}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, display: 'flex', flexDirection: 'column', gap: 18 };
const card = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '18px 20px' };
const thStyle = { textAlign: 'left', padding: '10px 12px', background: 'rgba(255,255,255,0.04)', fontSize: 11, textTransform: 'uppercase', color: '#64748b' };
const tdStyle = { padding: '12px', fontSize: 12, borderBottom: '1px solid rgba(255,255,255,0.05)', color: '#94a3b8' };
const ghostBtn = { padding: '6px 12px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.1)', background: 'transparent', color: '#e2e8f0', fontSize: 11, cursor: 'pointer', fontWeight: 600 };
const primaryBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '9px 18px', borderRadius: 10, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontSize: 12, fontWeight: 700, cursor: 'pointer', boxShadow: '0 4px 14px rgba(124,58,237,0.3)' };
