// NOTE: recharts is not yet in package.json.
// Run: npm install recharts --workspace=@supporthub/admin-portal
// The chart components below will render as placeholder divs until recharts is installed.

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner } from '@supporthub/ui';
import { useAuthStore } from '../store/authStore.js';
import {
  getDashboardSummary,
  getSlaCompliance,
  getAgentPerformance,
  getTicketTrend,
  getTicketsByCategory,
} from '../api/adminApi.js';
import type {
  DashboardSummary,
  SlaComplianceResult,
  AgentPerformanceResult,
  TrendPoint,
  CategoryCount,
} from '../api/adminApi.js';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type TabId = 'overview' | 'sla' | 'agents' | 'export';

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10);
}

function toInstant(dateStr: string, endOfDay = false): string {
  if (endOfDay) {
    return `${dateStr}T23:59:59Z`;
  }
  return `${dateStr}T00:00:00Z`;
}

// ---------------------------------------------------------------------------
// Stat card
// ---------------------------------------------------------------------------

interface StatCardProps {
  title: string;
  value: string | number;
  description: string;
}

function StatCard({ title, value, description }: StatCardProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-gray-500">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-bold text-gray-900">{value}</p>
        <p className="mt-1 text-xs text-gray-500">{description}</p>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Chart placeholder (replace with recharts components once installed)
// ---------------------------------------------------------------------------

interface ChartPlaceholderProps {
  title: string;
  data: unknown[];
  height?: number;
}

function ChartPlaceholder({ title, data, height = 200 }: ChartPlaceholderProps) {
  return (
    <div
      className="flex items-center justify-center rounded-md border border-dashed border-gray-300 bg-gray-50 text-sm text-gray-400"
      style={{ height }}
    >
      {title} — {data.length} data point{data.length !== 1 ? 's' : ''} (install recharts to render)
    </div>
  );
}

// ---------------------------------------------------------------------------
// Overview tab
// ---------------------------------------------------------------------------

interface OverviewTabProps {
  token: string;
  from: string;
  to: string;
}

function OverviewTab({ token, from, to }: OverviewTabProps) {
  const fromInstant = toInstant(from);
  const toInstant_ = toInstant(to, true);

  const { data: summary, isLoading: summaryLoading } = useQuery<DashboardSummary>({
    queryKey: ['reports', 'summary', from, to],
    queryFn: () => getDashboardSummary(token, fromInstant, toInstant_),
    enabled: token !== '',
  });

  const { data: trend, isLoading: trendLoading } = useQuery<TrendPoint[]>({
    queryKey: ['reports', 'trend', from, to],
    queryFn: () => getTicketTrend(token, fromInstant, toInstant_),
    enabled: token !== '',
  });

  const { data: byCategory, isLoading: catLoading } = useQuery<CategoryCount[]>({
    queryKey: ['reports', 'by-category', from, to],
    queryFn: () => getTicketsByCategory(token, fromInstant, toInstant_),
    enabled: token !== '',
  });

  const loading = summaryLoading || trendLoading || catLoading;

  if (loading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Spinner size="lg" label="Loading overview..." />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <StatCard
          title="Open Tickets"
          value={summary?.openTickets ?? '—'}
          description="In period"
        />
        <StatCard
          title="Resolved Tickets"
          value={summary?.resolvedTickets ?? '—'}
          description="In period"
        />
        <StatCard
          title="SLA Breaches"
          value={summary?.slaBreachCount ?? '—'}
          description="In period"
        />
        <StatCard
          title="Avg. Resolution"
          value={
            summary != null
              ? `${Math.round(summary.avgResolutionTimeMinutes)}m`
              : '—'
          }
          description="In period"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Tickets by Category</CardTitle>
          </CardHeader>
          <CardContent>
            {/* BarChart: replace ChartPlaceholder with recharts BarChart when installed */}
            <ChartPlaceholder title="Tickets by Category" data={byCategory ?? []} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Daily Ticket Trend</CardTitle>
          </CardHeader>
          <CardContent>
            {/* LineChart: replace ChartPlaceholder with recharts LineChart when installed */}
            <ChartPlaceholder title="Daily Ticket Trend" data={trend ?? []} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// SLA compliance tab
// ---------------------------------------------------------------------------

interface SlaTabProps {
  token: string;
  from: string;
  to: string;
}

function SlaTab({ token, from, to }: SlaTabProps) {
  const fromInstant = toInstant(from);
  const toInstant_ = toInstant(to, true);

  const { data: slaData, isLoading } = useQuery<SlaComplianceResult[]>({
    queryKey: ['reports', 'sla-compliance', from, to],
    queryFn: () => getSlaCompliance(token, fromInstant, toInstant_),
    enabled: token !== '',
  });

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Spinner size="lg" label="Loading SLA data..." />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-sm">SLA Compliance by Category</CardTitle>
        </CardHeader>
        <CardContent>
          {/* BarChart grouped by category: replace with recharts BarChart when installed */}
          <ChartPlaceholder title="SLA Compliance %" data={slaData ?? []} />
        </CardContent>
      </Card>

      <div className="overflow-x-auto rounded-md border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-gray-600">Category</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">Total Tickets</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">On Time</th>
              <th className="px-4 py-3 text-right font-medium text-gray-600">Compliance %</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {(slaData ?? []).map((row) => (
              <tr key={row.categoryName}>
                <td className="px-4 py-3 text-gray-900">{row.categoryName}</td>
                <td className="px-4 py-3 text-right text-gray-700">{row.totalTickets}</td>
                <td className="px-4 py-3 text-right text-gray-700">{row.onTimeTickets}</td>
                <td className="px-4 py-3 text-right font-semibold text-gray-900">
                  {row.compliancePercent.toFixed(1)}%
                </td>
              </tr>
            ))}
            {(slaData ?? []).length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-gray-400">
                  No data for the selected period.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Agent performance tab
// ---------------------------------------------------------------------------

type SortField = 'ticketsResolved' | 'avgResolutionMinutes' | 'firstResponseAvgMinutes';
type SortDir = 'asc' | 'desc';

interface AgentTabProps {
  token: string;
  from: string;
  to: string;
}

function AgentTab({ token, from, to }: AgentTabProps) {
  const [sortField, setSortField] = useState<SortField>('ticketsResolved');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const fromInstant = toInstant(from);
  const toInstant_ = toInstant(to, true);

  const { data: agentData, isLoading } = useQuery<AgentPerformanceResult[]>({
    queryKey: ['reports', 'agent-performance', from, to],
    queryFn: () => getAgentPerformance(token, fromInstant, toInstant_),
    enabled: token !== '',
  });

  const handleSort = (field: SortField) => {
    if (field === sortField) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  };

  const sorted = [...(agentData ?? [])].sort((a, b) => {
    const diff = a[sortField] - b[sortField];
    return sortDir === 'asc' ? diff : -diff;
  });

  const sortIndicator = (field: SortField) => {
    if (field !== sortField) return null;
    return sortDir === 'asc' ? ' ▲' : ' ▼';
  };

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Spinner size="lg" label="Loading agent data..." />
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-md border border-gray-200">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left font-medium text-gray-600">Agent</th>
            <th
              className="cursor-pointer px-4 py-3 text-right font-medium text-gray-600 hover:text-blue-600"
              onClick={() => { handleSort('ticketsResolved'); }}
            >
              Tickets Resolved{sortIndicator('ticketsResolved')}
            </th>
            <th
              className="cursor-pointer px-4 py-3 text-right font-medium text-gray-600 hover:text-blue-600"
              onClick={() => { handleSort('avgResolutionMinutes'); }}
            >
              Avg Resolution (min){sortIndicator('avgResolutionMinutes')}
            </th>
            <th
              className="cursor-pointer px-4 py-3 text-right font-medium text-gray-600 hover:text-blue-600"
              onClick={() => { handleSort('firstResponseAvgMinutes'); }}
            >
              Avg First Response (min){sortIndicator('firstResponseAvgMinutes')}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 bg-white">
          {sorted.map((row) => (
            <tr key={row.agentId}>
              <td className="px-4 py-3 text-gray-900">
                <span className="font-mono text-xs text-gray-500">{row.agentId}</span>
              </td>
              <td className="px-4 py-3 text-right font-semibold text-gray-900">
                {row.ticketsResolved}
              </td>
              <td className="px-4 py-3 text-right text-gray-700">
                {row.avgResolutionMinutes.toFixed(1)}
              </td>
              <td className="px-4 py-3 text-right text-gray-700">
                {row.firstResponseAvgMinutes.toFixed(1)}
              </td>
            </tr>
          ))}
          {sorted.length === 0 && (
            <tr>
              <td colSpan={4} className="px-4 py-6 text-center text-gray-400">
                No data for the selected period.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Export tab
// ---------------------------------------------------------------------------

interface ExportTabProps {
  token: string;
}

function ExportTab({ token }: ExportTabProps) {
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);
  const [downloading, setDownloading] = useState(false);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const fromInstant = toInstant(from);
      const toInstant_ = toInstant(to, true);
      const url = `/api/v1/reports/export?from=${encodeURIComponent(fromInstant)}&to=${encodeURIComponent(toInstant_)}`;
      const res = await fetch(url, {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Tenant-ID': import.meta.env.VITE_TENANT_ID,
        },
      });
      if (!res.ok) throw new Error(`Export failed: ${res.status}`);
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = blobUrl;
      anchor.download = `tickets-export-${from}-${to}.csv`;
      anchor.click();
      URL.revokeObjectURL(blobUrl);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle className="text-sm">Export Tickets to CSV</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">From</label>
            <input
              type="date"
              value={from}
              onChange={(e) => { setFrom(e.target.value); }}
              className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">To</label>
            <input
              type="date"
              value={to}
              onChange={(e) => { setTo(e.target.value); }}
              className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            onClick={() => { void handleDownload(); }}
            disabled={downloading}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {downloading ? 'Downloading…' : 'Download CSV'}
          </button>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

const TABS: { id: TabId; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'sla', label: 'SLA Compliance' },
  { id: 'agents', label: 'Agent Performance' },
  { id: 'export', label: 'Export' },
];

export function ReportingPage() {
  const { token } = useAuthStore();
  const [activeTab, setActiveTab] = useState<TabId>('overview');
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);

  const safeToken = token ?? '';

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Reporting</h2>
          <p className="mt-1 text-sm text-gray-500">Analytics and exports for your support operations</p>
        </div>

        {activeTab !== 'export' && (
          <div className="flex items-center gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-500">From</label>
              <input
                type="date"
                value={from}
                onChange={(e) => { setFrom(e.target.value); }}
                className="mt-0.5 rounded-md border border-gray-300 px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500">To</label>
              <input
                type="date"
                value={to}
                onChange={(e) => { setTo(e.target.value); }}
                className="mt-0.5 rounded-md border border-gray-300 px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
        )}
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-gray-200">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            onClick={() => { setActiveTab(tab.id); }}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              activeTab === tab.id
                ? 'border-b-2 border-blue-600 text-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'overview' && <OverviewTab token={safeToken} from={from} to={to} />}
      {activeTab === 'sla' && <SlaTab token={safeToken} from={from} to={to} />}
      {activeTab === 'agents' && <AgentTab token={safeToken} from={from} to={to} />}
      {activeTab === 'export' && <ExportTab token={safeToken} />}
    </div>
  );
}
