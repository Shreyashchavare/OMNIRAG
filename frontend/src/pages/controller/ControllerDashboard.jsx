import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  FileText, Cpu, FlaskConical, ClipboardCheck, RefreshCw, AlertCircle,
} from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { processingApi } from '../../api/services/processingApi';
import { analyticsApi } from '../../api/services/analyticsApi';
import { extractErrorMessage } from '../../api/client';
import { weeklyData, models } from '../../data/mockData';

export default function ControllerDashboard() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState([]);
  const [dashboardData, setDashboardData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    Promise.allSettled([
      processingApi.getJobs(null, 0, 5),
      analyticsApi.getDashboard(),
    ]).then(([jobsRes, dashRes]) => {
      if (!ignore) {
        if (jobsRes.status === 'fulfilled' && jobsRes.value?.content) {
          setJobs(jobsRes.value.content);
        }
        if (dashRes.status === 'fulfilled') {
          setDashboardData(dashRes.value);
        }
        setLoading(false);
      }
    }).catch((err) => {
      if (!ignore) {
        setError(extractErrorMessage(err));
        setLoading(false);
      }
    });

    return () => {
      ignore = true;
    };
  }, []);

  const handleRefresh = async () => {
    setLoading(true);
    setError(null);
    try {
      const [jobsRes, dashRes] = await Promise.allSettled([
        processingApi.getJobs(null, 0, 5),
        analyticsApi.getDashboard(),
      ]);
      if (jobsRes.status === 'fulfilled' && jobsRes.value?.content) {
        setJobs(jobsRes.value.content);
      }
      if (dashRes.status === 'fulfilled') {
        setDashboardData(dashRes.value);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const docs = dashboardData?.documents;
  const uploadData = weeklyData.map((d) => ({ ...d, uploads: Math.round(d.queries * 0.15) }));

  return (
    <div style={wrap}>
      {/* Header & Refresh */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Controller Operations Console</h2>
          <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
            Monitor ingestion jobs, model orchestration, and data pipeline throughput
          </p>
        </div>

        <button
          onClick={handleRefresh}
          style={refreshBtn}
          disabled={loading}
        >
          <RefreshCw size={13} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} />
          <span>{loading ? 'Syncing...' : 'Sync Pipeline'}</span>
        </button>
      </div>

      {error && (
        <div style={errorBox}>
          <AlertCircle size={15} color="#ef4444" />
          <span>{error}</span>
        </div>
      )}

      {/* KPI Cards */}
      <div style={grid4}>
        <StatCard
          label="Documents Ingested"
          value={docs ? docs.total.toString() : '24'}
          change={docs ? `${docs.processed} processed` : '+3 this week'}
          icon={FileText}
          color="#6366f1"
          onClick={() => navigate('/controller/upload')}
        />
        <StatCard
          label="Models Configured"
          value="3"
          change="HNSW &amp; FAISS ready"
          icon={Cpu}
          color="#8b5cf6"
          onClick={() => navigate('/controller/models')}
        />
        <StatCard
          label="Inference Tests"
          value="87"
          change="+12 completed"
          icon={FlaskConical}
          color="#06b6d4"
          onClick={() => navigate('/controller/testing')}
        />
        <StatCard
          label="Pending Queue"
          value={jobs.length ? jobs.filter((j) => j.status === 'QUEUED' || j.status === 'PROCESSING').length.toString() : '5'}
          change="Review required"
          icon={ClipboardCheck}
          color="#f59e0b"
          onClick={() => navigate('/controller/approval')}
        />
      </div>

      {/* Chart & Active Models Row */}
      <div style={grid2}>
        <div style={card}>
          <div style={cardTitle}>Document Ingestion Velocity — 7 Days</div>
          <ResponsiveContainer width="100%" height={175}>
            <AreaChart data={uploadData}>
              <defs>
                <linearGradient id="ctrlUpGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#7c3aed" stopOpacity={0.35} />
                  <stop offset="95%" stopColor="#7c3aed" stopOpacity={0} />
                </linearGradient>
              </defs>
              <XAxis dataKey="day" tick={tick} axisLine={false} tickLine={false} />
              <YAxis tick={tick} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={ttStyle} />
              <Area type="monotone" dataKey="uploads" stroke="#a855f7" fill="url(#ctrlUpGrad)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div style={card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
            <span style={cardTitle}>RAG Embedding &amp; LLM Models</span>
            <button onClick={() => navigate('/controller/models')} style={linkBtn}>
              Manage →
            </button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {models.map((m) => (
              <div key={m.name} style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: '9px 0', borderBottom: '1px solid rgba(255,255,255,0.04)',
              }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13, color: '#f1f5f9' }}>{m.name}</div>
                  <div style={{ fontSize: 11, color: '#64748b', marginTop: 2 }}>
                    Provider: {m.provider} · Latency: {m.latency}
                  </div>
                </div>
                <span style={{
                  fontSize: 10, padding: '2px 8px', borderRadius: 99, fontWeight: 700,
                  background: m.status === 'Running' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                  color: m.status === 'Running' ? '#10b981' : '#ef4444',
                }}>
                  {m.status}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Recent Processing Jobs Table */}
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <span style={cardTitle}>Live Processing Jobs (Processing Service)</span>
          <button onClick={() => navigate('/controller/upload')} style={linkBtn}>
            Launch New Job →
          </button>
        </div>

        {jobs.length === 0 ? (
          <div style={{ padding: '24px 0', textAlign: 'center', color: '#64748b', fontSize: 13 }}>
            No recent processing jobs returned from Processing Service. Click &quot;Launch New Job&quot; in Upload Docs to start an ingest pipeline.
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr>
                {['Job ID', 'Document UUID', 'Stage', 'Status', 'Attempts', 'Created At'].map((h) => (
                  <th key={h} style={thStyle}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id}>
                  <td style={tdStyle}>
                    <code style={{ color: '#a5b4fc', fontSize: 12 }}>{job.id.slice(0, 8)}...</code>
                  </td>
                  <td style={tdStyle}>
                    <code style={{ color: '#94a3b8', fontSize: 12 }}>{job.documentId.slice(0, 8)}...</code>
                  </td>
                  <td style={tdStyle}>
                    <span style={{ fontSize: 11, color: '#e2e8f0', fontWeight: 600 }}>
                      {job.stage || 'EXTRACTION'}
                    </span>
                  </td>
                  <td style={tdStyle}>
                    <span style={{
                      fontSize: 10, padding: '2px 8px', borderRadius: 6, fontWeight: 700,
                      background: job.status === 'COMPLETED' ? 'rgba(16,185,129,0.15)' : job.status === 'FAILED' ? 'rgba(239,68,68,0.15)' : 'rgba(99,102,241,0.15)',
                      color: job.status === 'COMPLETED' ? '#10b981' : job.status === 'FAILED' ? '#ef4444' : '#a5b4fc',
                    }}>
                      {job.status}
                    </span>
                  </td>
                  <td style={tdStyle}>{job.attemptCount || 1} / {job.maxAttempts || 3}</td>
                  <td style={tdStyle}>{job.createdAt ? new Date(job.createdAt).toLocaleTimeString() : 'Recent'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, change, icon: Icon, color, onClick }) {
  return (
    <div
      style={{ ...card, cursor: onClick ? 'pointer' : 'default', transition: 'border-color 0.2s' }}
      onClick={onClick}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <div>
          <div style={{ color: '#94a3b8', fontSize: 12, marginBottom: 6 }}>{label}</div>
          <div style={{ fontSize: 24, fontWeight: 900, color: '#f1f5f9' }}>{value}</div>
          <div style={{ fontSize: 11, color: '#34d399', marginTop: 4 }}>{change}</div>
        </div>
        <div style={{
          width: 38, height: 38, borderRadius: 10,
          background: `${color}1a`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon size={18} color={color} />
        </div>
      </div>
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, display: 'flex', flexDirection: 'column', gap: 18 };
const grid4 = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 };
const grid2 = { display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 18 };
const card = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '18px 20px' };
const cardTitle = { fontWeight: 700, fontSize: 14, color: '#f1f5f9', marginBottom: 12 };
const thStyle = { textAlign: 'left', padding: '10px 12px', background: 'rgba(255,255,255,0.04)', fontSize: 11, textTransform: 'uppercase', color: '#64748b' };
const tdStyle = { padding: '12px', fontSize: 12, borderBottom: '1px solid rgba(255,255,255,0.05)', color: '#94a3b8' };
const linkBtn = { fontSize: 12, color: '#c084fc', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 };
const refreshBtn = { display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 12, fontWeight: 600, cursor: 'pointer' };
const errorBox = { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 10, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#fca5a5', fontSize: 12 };
const tick = { fill: '#64748b', fontSize: 11 };
const ttStyle = { background: '#161926', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, color: '#f1f5f9', fontSize: 12 };
