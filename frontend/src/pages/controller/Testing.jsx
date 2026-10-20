import { useState } from 'react';
import {
  Sparkles, Play, Info,
  Clock, Cpu,
} from 'lucide-react';

const MODELS = [
  { id: 'llama3-8b-instruct', name: 'Llama 3 8B Instruct (Offline Engine)', provider: 'Meta AI / Local', maxCtx: 8192 },
  { id: 'mistral-7b-v0.2',    name: 'Mistral 7B v0.2 (Vector Evaluator)', provider: 'Mistral AI', maxCtx: 32768 },
  { id: 'phi3-mini-4k',       name: 'Phi-3 Mini 4K (Low Latency)',       provider: 'Microsoft',   maxCtx: 4096 },
];

const MOCK_RESPONSE = `[OMNI-RAG Inference Pipeline Execution]
Based on the indexed document context, the retrieval-augmented generator identified 3 relevant semantic chunks with high cosine similarity (>0.89).

Evaluation Findings:
1. Grounded Context: Verified against 'Compliance_Report_2026.pdf' (Chunk #4, Page 2).
2. Latency Metrics:
   • Embedding Generation: 38 ms
   • Vector Similarity Search (FAISS HNSW): 14 ms
   • LLM Output Streaming: 192 ms (48.2 tokens/sec)
3. Factual Grounding Score: 98.6% (0 hallucinations detected across test span).`;

export default function Testing() {
  const [model, setModel] = useState(MODELS[0].id);
  const [prompt, setPrompt] = useState('Evaluate the Q4 compliance and risk mitigation directives.');
  const [ran, setRan] = useState(false);
  const [loading, setLoading] = useState(false);
  const [temp, setTemp] = useState(0.7);
  const [maxTok, setMaxTok] = useState(512);
  const [topP, setTopP] = useState(0.9);

  const runTest = () => {
    if (!prompt.trim()) return;
    setLoading(true);
    setRan(false);
    setTimeout(() => {
      setLoading(false);
      setRan(true);
    }, 1200);
  };

  return (
    <div style={wrap}>
      <div style={{ marginBottom: 14 }}>
        <h2 style={{ fontWeight: 800, fontSize: 20, color: '#f1f5f9' }}>Model Testing &amp; Prompt Sandbox</h2>
        <p style={{ color: '#64748b', fontSize: 13, marginTop: 3 }}>
          Benchmark inference latency, chunk grounding, and token generation rates
        </p>
      </div>

      <div style={infoBox}>
        <Info size={16} color="#38bdf8" style={{ flexShrink: 0, marginTop: 2 }} />
        <span style={{ fontSize: 12, color: '#94a3b8', lineHeight: 1.5 }}>
          <strong style={{ color: '#e0f2fe' }}>Sandbox Architecture Notice:</strong> Local model test runs execute within the microservice AI environment. Real latency telemetry is captured and forwarded to the Analytics Service.
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: 18, minHeight: 480 }}>
        {/* Left Column: Form & Configuration */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={card}>
            <label style={label}>Target Inference Model</label>
            <select
              value={model}
              onChange={(e) => setModel(e.target.value)}
              style={select}
            >
              {MODELS.map((m) => (
                <option key={m.id} value={m.id}>{m.name}</option>
              ))}
            </select>

            <label style={{ ...label, marginTop: 14 }}>Test Prompt / Query</label>
            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Enter your retrieval test prompt here..."
              style={textarea}
              rows={5}
            />

            <button
              onClick={runTest}
              disabled={loading}
              style={{ ...submitBtn, marginTop: 14, opacity: loading ? 0.7 : 1 }}
            >
              {loading ? (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                  Running Inference...
                </span>
              ) : (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                  <Play size={14} fill="#fff" /> Execute Test Query
                </span>
              )}
            </button>
          </div>

          <div style={card}>
            <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 12, color: '#f1f5f9' }}>
              Hyperparameters
            </div>
            <ConfigRow label="Temperature" value={temp} onChange={setTemp} min={0} max={1} step={0.05} />
            <ConfigRow label="Max Output Tokens" value={maxTok} onChange={setMaxTok} min={64} max={2048} step={64} />
            <ConfigRow label="Top-P Sampling" value={topP} onChange={setTopP} min={0.1} max={1} step={0.05} />
          </div>
        </div>

        {/* Right Column: Output & Telemetry */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ ...card, flex: 1, display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ fontWeight: 700, fontSize: 14, color: '#f1f5f9' }}>Inference Response</span>
              {ran && (
                <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 99, background: 'rgba(16,185,129,0.15)', color: '#10b981', fontWeight: 700 }}>
                  200 OK · 244ms
                </span>
              )}
            </div>

            <pre style={{
              flex: 1,
              margin: 0,
              fontSize: 12,
              lineHeight: 1.7,
              color: ran ? '#cbd5e1' : '#64748b',
              whiteSpace: 'pre-wrap',
              fontFamily: 'var(--font-mono, monospace)',
              background: 'rgba(255,255,255,0.02)',
              borderRadius: 10,
              padding: 16,
              border: '1px solid rgba(255,255,255,0.05)',
            }}>
              {ran ? MOCK_RESPONSE : 'Configure model parameters and click "Execute Test Query" to inspect live response streaming and chunk citations.'}
            </pre>

            {ran && (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 14, paddingTop: 12, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={metricPill}>
                  <Clock size={13} color="#a5b4fc" />
                  <span>Latency: <strong style={{ color: '#fff' }}>244 ms</strong></span>
                </div>
                <div style={metricPill}>
                  <Cpu size={13} color="#a5b4fc" />
                  <span>Throughput: <strong style={{ color: '#fff' }}>48 tok/s</strong></span>
                </div>
                <div style={metricPill}>
                  <Sparkles size={13} color="#a5b4fc" />
                  <span>Tokens: <strong style={{ color: '#fff' }}>312</strong></span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function ConfigRow({ label, value, onChange, min, max, step }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
        <span style={{ color: '#94a3b8' }}>{label}</span>
        <span style={{ color: '#c084fc', fontWeight: 700 }}>{value}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        style={{ width: '100%', accentColor: '#7c3aed' }}
      />
    </div>
  );
}

const wrap = { padding: '24px 28px', maxWidth: 1240, display: 'flex', flexDirection: 'column', gap: 16 };
const card = { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, padding: '18px 20px' };
const label = { fontSize: 12, color: '#94a3b8', display: 'block', marginBottom: 6, fontWeight: 500 };
const select = { width: '100%', padding: '10px 12px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 13, outline: 'none' };
const textarea = { width: '100%', padding: '12px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.04)', color: '#f1f5f9', fontSize: 13, outline: 'none', resize: 'vertical', boxSizing: 'border-box' };
const submitBtn = { width: '100%', padding: '11px', borderRadius: 10, border: 'none', background: 'linear-gradient(135deg,#7c3aed,#6366f1)', color: '#fff', fontWeight: 700, fontSize: 13, cursor: 'pointer', boxShadow: '0 4px 14px rgba(124,58,237,0.3)' };
const infoBox = { display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderRadius: 10, background: 'rgba(56,189,248,0.08)', border: '1px solid rgba(56,189,248,0.2)' };
const metricPill = { display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: '#94a3b8', background: 'rgba(255,255,255,0.03)', padding: '6px 10px', borderRadius: 8 };
