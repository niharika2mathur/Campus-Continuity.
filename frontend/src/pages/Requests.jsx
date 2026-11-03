import { useState } from 'react';
import { ScanSearch } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, CardHeader, Empty, Field, Loadable, Note, PageHeader, Table, THead, useToast } from '../components/ui.jsx';
import { download, toCsv, when } from '../lib/util.js';

const bloomTone = { NEGATIVE: 'good', POSSIBLE: 'warn' };
const outTone = { ACCEPTED: 'good', REJECTED_DUPLICATE: 'bad', REJECTED_INVALID: 'bad' };

export default function Requests() {
  const log = useApi(() => api.get('/requests'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [id, setId] = useState(''); const [res, setRes] = useState(null);
  const screen = () => run(async () => { setRes(await api.post('/requests/screen', { requestId: id })); log.reload(); });

  return (
    <>
      <PageHeader title="Request management" subtitle="Audit trail of every request identifier screened by the Bloom filter, with the exact verification outcome."
        actions={<button className="btn-ghost" disabled={!log.data?.length} onClick={() => download('request-log.csv', toCsv(log.data))}>Export CSV</button>} />
      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-1 self-start">
          <CardHeader title="Screen an identifier" hint="Read-only probe — does not create a request" icon={ScanSearch} />
          <div className="space-y-3 p-4">
            <Field label="Request id"><input className="input font-mono" value={id} onChange={(e) => setId(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && id.trim() && screen()} placeholder="REQ-2026-0001" /></Field>
            <button className="btn-primary w-full" disabled={!id.trim() || busy} onClick={screen}>Screen</button>
            {res && <div className="fade-in space-y-2 rounded-lg border border-ink-600 bg-ink-900 p-3">
              <div className="flex flex-wrap gap-2"><Badge tone={bloomTone[res.bloomResult]}>Bloom: {res.bloomResult}</Badge><Badge tone={res.exactResult === 'PRESENT' ? 'bad' : 'neutral'}>Exact: {res.exactResult}</Badge></div>
              <p className="text-xs leading-relaxed text-ink-200">{res.explanation}</p>
              <p className="font-mono text-[11px] text-ink-400">bit positions: {res.positions.join(', ')}</p></div>}
            <Note>A Bloom “possible” result is never treated as proof. Only the database lookup decides whether a request is a duplicate.</Note>
          </div>
        </Card>
        <Card className="xl:col-span-2">
          <CardHeader title="Screening log" hint="Most recent 300 events" />
          <Loadable state={log}>{(rows) => rows.length === 0 ? <Empty title="No requests screened yet" text="Register a team or import teams.csv." /> : (
            <Table><THead cols={['When', 'Request id', 'Team', 'Bloom', 'Exact check', 'Outcome', 'Source']} /><tbody>{rows.map((r) => (
              <tr key={r.id} className="border-b border-ink-700/60"><td className="td text-xs text-ink-400">{when(r.createdAt)}</td><td className="td font-mono text-xs">{r.requestId}</td><td className="td">{r.teamCode}</td>
                <td className="td"><Badge tone={bloomTone[r.bloomResult]}>{r.bloomResult}</Badge></td><td className="td text-xs text-ink-300">{r.exactResult.replace('_', ' ')}</td>
                <td className="td"><Badge tone={outTone[r.outcome]}>{r.outcome.replace('_', ' ')}</Badge>{r.message && <div className="mt-1 text-[11px] text-ink-400">{r.message}</div>}</td><td className="td text-xs text-ink-400">{r.source}</td></tr>))}</tbody></Table>)}</Loadable>
        </Card>
      </div>
    </>
  );
}
