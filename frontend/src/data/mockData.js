// ─── MOCK DATA ────────────────────────────────────────────────────────────────

export const documents = [
  { id: 1, name: "Q4_Financial_Report.pdf",   type: "pdf",   chunks: 312, embeddings: 312, size: "2.4 MB",  status: "Processed",  date: "2025-05-14" },
  { id: 2, name: "Product_Roadmap_2025.pdf",  type: "pdf",   chunks: 178, embeddings: 178, size: "1.1 MB",  status: "Processed",  date: "2025-05-13" },
  { id: 3, name: "Customer_Survey.pdf",       type: "pdf",   chunks: 245, embeddings: 245, size: "3.2 MB",  status: "Processed",  date: "2025-05-12" },
  { id: 4, name: "Tech_Architecture.pdf",     type: "pdf",   chunks: 489, embeddings: 489, size: "5.7 MB",  status: "Processed",  date: "2025-05-11" },
  { id: 5, name: "Meeting_Recording.mp3",     type: "audio", chunks: 134, embeddings: 134, size: "18.4 MB", status: "Processed",  date: "2025-05-10" },
  { id: 6, name: "Product_Demo.mp4",          type: "video", chunks: 67,  embeddings: 0,   size: "124 MB",  status: "Processing", date: "2025-05-09" },
  { id: 7, name: "Org_Chart.png",             type: "image", chunks: 23,  embeddings: 23,  size: "0.8 MB",  status: "Processed",  date: "2025-05-08" },
];

export const queryHistory = [
  { id: 1, query: "What were the key findings in Q4 revenue?",      time: "2m ago",  tokens: 847,  docs: 2 },
  { id: 2, query: "Summarize the product roadmap priorities",        time: "15m ago", tokens: 1203, docs: 3 },
  { id: 3, query: "What did customers say about UX issues?",         time: "1h ago",  tokens: 634,  docs: 1 },
  { id: 4, query: "Explain the microservices architecture",          time: "3h ago",  tokens: 2104, docs: 2 },
  { id: 5, query: "Who is responsible for Q1 delivery?",             time: "5h ago",  tokens: 412,  docs: 1 },
];

export const vectorStats = {
  totalVectors:  1448,
  dimensions:    1536,
  indexSize:     "284 MB",
  avgQueryTime:  "43ms",
  cacheHitRate:  "71%",
  model:         "text-embedding-3-large",
};

export const weeklyData = [
  { day: "Mon", queries: 124, tokens: 48 },
  { day: "Tue", queries: 189, tokens: 72 },
  { day: "Wed", queries: 203, tokens: 84 },
  { day: "Thu", queries: 167, tokens: 61 },
  { day: "Fri", queries: 241, tokens: 97 },
  { day: "Sat", queries: 88,  tokens: 34 },
  { day: "Sun", queries: 62,  tokens: 24 },
];

export const docTypeData = [
  { name: "PDF",   value: 68, color: "#e11d48" },
  { name: "Audio", value: 14, color: "#7c3aed" },
  { name: "Video", value: 9,  color: "#0891b2" },
  { name: "Image", value: 9,  color: "#059669" },
];

export const initChatMessages = [
  {
    role: "user",
    content: "What were the main revenue highlights from Q4?",
    time: "10:24 AM",
  },
  {
    role: "assistant",
    content: "Based on the Q4 Financial Report, here are the key highlights:",
    bullets: [
      "Total revenue reached $47.2M — 23% YoY growth",
      "SaaS ARR grew to $38.1M driven by enterprise expansion",
      "NRR hit 118%, the highest in company history",
      "3 new enterprise logos each worth >$1M ARR",
    ],
    citations: [
      { doc: "Q4_Financial_Report.pdf", page: 4 },
      { doc: "Q4_Financial_Report.pdf", page: 7 },
    ],
    time: "10:24 AM",
  },
  {
    role: "user",
    content: "Which product areas drove the most growth?",
    time: "10:26 AM",
  },
  {
    role: "assistant",
    content: "According to the documents, growth was primarily driven by:",
    bullets: [
      "AI/ML features added $8.4M in new ARR (+31% vs target)",
      "Enterprise integrations drove 67% of expansion revenue",
      "Analytics upgrades contributed to 22% churn reduction",
    ],
    citations: [
      { doc: "Q4_Financial_Report.pdf",  page: 11 },
      { doc: "Product_Roadmap_2025.pdf", page: 2  },
    ],
    time: "10:26 AM",
  },
];

export const ocrText = `QUARTERLY FINANCIAL REPORT — Q4 2025

Executive Summary
Total revenue for Q4 2025 reached $47.2 million, representing a
year-over-year growth of 23%. This exceeded our forecast of $43.8M
by 7.8%.

Key Performance Indicators
• Annual Recurring Revenue (ARR): $38.1M (+29% YoY)
• Net Revenue Retention: 118%
• Gross Margin: 74.2%
• New Logos: 47 (3 enterprise >$1M ARR)

Revenue Breakdown by Segment
Enterprise:  $28.4M (60.2%)
Mid-Market:  $12.1M (25.6%)
SMB:          $6.7M (14.2%)`;

export const docChunks = [
  { id: 1, text: "Total revenue for Q4 2025 reached $47.2 million, representing YoY growth of 23%, exceeding forecast.", sim: 0.94, page: 1 },
  { id: 2, text: "Annual Recurring Revenue grew to $38.1M driven by strong enterprise expansion deals in the quarter.",   sim: 0.91, page: 2 },
  { id: 3, text: "Net Revenue Retention hit 118%, the highest recorded metric in the company's operating history.",        sim: 0.88, page: 2 },
  { id: 4, text: "Q4 saw 3 new enterprise logos each worth more than $1M ARR, finishing ahead of the quarterly plan.",    sim: 0.85, page: 3 },
  { id: 5, text: "Gross margin improved to 74.2%, reflecting continued operational efficiency gains across the business.", sim: 0.82, page: 4 },
];

export const sampleUploadNames = [
  "Annual_Report_2025.pdf",
  "Q1_Meeting_Notes.pdf",
  "Sales_Data_Analysis.pdf",
  "Technical_Specs_v3.pdf",
  "Customer_Feedback.pdf",
];

// ─── CONTROLLER MOCK DATA ─────────────────────────────────────────────────────
export const models = [
  { name: "llama3-8b-instruct", provider: "Meta",      status: "Running", ram: "4.2 GB", latency: "187ms", requests: 423 },
  { name: "mistral-7b-v0.2",    provider: "Mistral",   status: "Running", ram: "3.8 GB", latency: "203ms", requests: 318 },
  { name: "phi3-mini-4k",       provider: "Microsoft", status: "Stopped", ram: "0 GB",   latency: "—",     requests: 0   },
];

export const pendingApprovals = [
  { id: 1, doc: "Annual_Policy_2025.pdf", by: "Rahul Kumar",  size: "1.2 MB", date: "Today, 10:30" },
  { id: 2, doc: "Safety_Handbook.pdf",    by: "Anjali Singh", size: "3.4 MB", date: "Today, 09:15" },
  { id: 3, doc: "HR_Guidelines_v3.pdf",   by: "Vikram Patel", size: "0.9 MB", date: "Yesterday"    },
];

// ─── ADMIN MOCK DATA ──────────────────────────────────────────────────────────
export const users = [
  { id:1, name:"Arjun Sharma",  email:"arjun@omrag.ai",  role:"Admin",      status:"Active",    lastLogin:"Just now"  },
  { id:2, name:"Priya Mehta",   email:"priya@omrag.ai",  role:"Controller", status:"Active",    lastLogin:"5m ago"    },
  { id:3, name:"Rahul Kumar",   email:"rahul@omrag.ai",  role:"User",       status:"Active",    lastLogin:"1h ago"    },
  { id:4, name:"Sneha Tiwari",  email:"sneha@omrag.ai",  role:"Controller", status:"Suspended", lastLogin:"2d ago"    },
  { id:5, name:"Vikram Patel",  email:"vikram@omrag.ai", role:"User",       status:"Active",    lastLogin:"30m ago"   },
  { id:6, name:"Anjali Singh",  email:"anjali@omrag.ai", role:"User",       status:"Active",    lastLogin:"3h ago"    },
  { id:7, name:"Deepak Rao",    email:"deepak@omrag.ai", role:"User",       status:"Suspended", lastLogin:"5d ago"    },
  { id:8, name:"Kavita Nair",   email:"kavita@omrag.ai", role:"Controller", status:"Active",    lastLogin:"Yesterday" },
];

export const chatHistoryData = [
  { id:1, title:"Q4 Revenue Analysis",       date:"Today",      messages:6,  preview:"What were the key revenue findings in Q4?" },
  { id:2, title:"Product Roadmap Questions", date:"Yesterday",  messages:12, preview:"Summarize the product roadmap priorities..." },
  { id:3, title:"HR Policy Queries",         date:"2 days ago", messages:4,  preview:"What is the leave policy for contractors?" },
  { id:4, title:"Technical Architecture",    date:"Last week",  messages:9,  preview:"Explain the microservices architecture..." },
];
