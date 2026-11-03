import { useState } from 'react';
import { Download } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, Empty, Loadable, PageHeader, Table, Tabs, THead, useToast } from '../components/ui.jsx';
import { download, toCsv, when } from '../lib/util.js';

const KINDS = [['activity', 'Activity history'], ['ALLOCATION', 'Allocation runs'], ['SIMULATION', 'Simulation runs'], ['BENCHMARK', 'Benchmark runs']];
const ENDPOINT = { allocations: '/allocations', simulations: '/simulations', benchmarks: '/benchmarks' };
const PATH = { ALLOCATION: 'allocations', SIMULATION: 'simulations', BENCHMARK: 'benchmarks' };

export default function Reports() {
  const [tab, setTab] = useState('activity');
  const toast = useToast(); const [run, busy] = useAction(toast);
  const state = useApi(() => (tab === 'activity' ? api.get('/reports/activity') : api.get(`/reports/runs?kind=${tab}`)), [tab]);
  const full = (r) => run(async () => download(`${PATH[tab]}-${r.id}.json`, JSON.stringify(await api.get(`${ENDPOINT[PATH[tab]]}/${r.id}`), null, 2), 'application/json'));

  return (
    <>
      <PageHeader title="Reports & activity history" subtitle="Audit trail of changes and every stored algorithm run. Export any view as CSV, or download a full run as JSON."
        actions={<button className="btn-ghost" disabled={!state.data?.length} onClick={() => download(`${tab.toLowerCase()}-report.csv`, toCsv(state.data))}><Download size={15} />Export CSV</button>} />
      <Tabs tabs={KINDS.map(([id, label]) => ({ id, label }))} value={tab} onChange={setTab} />
      <Card><Loadable state={state}>{(rows) => rows.length === 0 ? <Empty title="Nothing recorded yet" text="Actions and runs appear here as you use the platform." /> : tab === 'activity' ? (
        <Table><THead cols={['When', 'Action', 'Detail']} /><tbody>{rows.map((r) => <tr key={r.id} className="border-b border-ink-700/50"><td className="td whitespace-nowrap text-xs text-ink-400">{when(r.createdAt)}</td><td className="td"><Badge tone="brand">{r.action}</Badge></td><td className="td text-xs text-ink-200">{r.detail}</td></tr>)}</tbody></Table>
      ) : (
        <Table><THead cols={['When', 'Name', 'Summary', '']} /><tbody>{rows.map((r) => <tr key={r.id} className="border-b border-ink-700/50"><td className="td whitespace-nowrap text-xs text-ink-400">{when(r.createdAt)}</td><td className="td">{r.name}</td><td className="td text-xs text-ink-200">{r.summary}</td><td className="td text-right"><button className="btn-ghost" disabled={busy} onClick={() => full(r)}><Download size={14} />JSON</button></td></tr>)}</tbody></Table>)}</Loadable></Card>
    </>
  );
}
