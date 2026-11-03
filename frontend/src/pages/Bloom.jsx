import { useState } from 'react';
import { Binary, FlaskRound, Plus, Search } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import BitGrid from '../components/BitGrid.jsx';
import { Badge, Card, CardHeader, Field, Loadable, Note, PageHeader, useToast } from '../components/ui.jsx';
import { num, pct } from '../lib/util.js';

const CLASS_TONE = { DEFINITELY_ABSENT: 'good', TRUE_POSITIVE: 'brand', CONFIRMED_DUPLICATE: 'bad', FALSE_POSITIVE: 'warn', INSERTED: 'neutral' };
const Metric = ({ k, v, tip }) => <div className="bg-ink-850 p-3 text-center" title={tip}><div className="text-base font-semibold tabular-nums text-white">{v}</div><div className="text-[11px] text-ink-300">{k}</div></div>;

function Metrics({ s }) {
  return (
    <div className="grid grid-cols-2 gap-px bg-ink-700 sm:grid-cols-4">
      <Metric k="Bits (m)" v={num(s.bits)} /><Metric k="Hash functions (k)" v={s.hashes} /><Metric k="Insertions" v={num(s.insertions)} tip={`Designed for ${s.expectedInsertions}`} /><Metric k="Bit occupancy" v={pct(s.occupancy, 1)} tip="Share of bits set to 1" />
      <Metric k="Target FPP" v={pct(s.targetFpp, 2)} /><Metric k="Predicted FPP now" v={pct(s.theoreticalFpp, 2)} tip="(1 − e^(−kn/m))^k" /><Metric k="Estimated FPP (fill^k)" v={pct(s.estimatedFpp, 2)} /><Metric k="Set bits" v={num(s.setBits)} />
    </div>);
}
function Stats({ s }) {
  return (
    <div className="grid grid-cols-3 gap-px bg-ink-700 sm:grid-cols-6">
      <Metric k="Queries" v={num(s.queries)} /><Metric k="Definite negatives" v={num(s.negativePrechecks)} /><Metric k="Possible positives" v={num(s.possiblePositives)} />
      <Metric k="Confirmed duplicates" v={num(s.confirmedDuplicates)} /><Metric k="False positives" v={num(s.falsePositives)} /><Metric k="False-positive rate" v={pct(s.falsePositiveRate, 2)} tip="false positives ÷ (false positives + definite negatives)" />
    </div>);
}
function Result({ r }) {
  return (
    <div className="fade-in space-y-2 rounded-lg border border-ink-600 bg-ink-900 p-3">
      <div className="flex flex-wrap items-center gap-2"><Badge tone={CLASS_TONE[r.classification]}>{r.classification.replace(/_/g, ' ')}</Badge><span className="text-xs text-ink-300">Bloom: {r.bloomResult} · Exact: {r.exactResult.replace('_', ' ')}</span></div>
      <p className="text-xs leading-relaxed text-ink-200">{r.explanation}</p>
      <div className="flex flex-wrap gap-1.5">{r.positions.map((p, i) => <span key={i} className={`rounded border px-1.5 py-0.5 font-mono text-[11px] ${r.bitsAtPositions[i] ? 'border-teal-500/40 bg-teal-500/10 text-teal-200' : 'border-red-500/40 bg-red-500/10 text-red-200'}`} title={`hash ${i + 1}`}>#{p}={r.bitsAtPositions[i] ? 1 : 0}</span>)}</div>
    </div>);
}

export default function Bloom() {
  const live = useApi(() => api.get('/bloom/live')); const box = useApi(() => api.get('/bloom/sandbox'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [liveId, setLiveId] = useState(''); const [liveRes, setLiveRes] = useState(null);
  const [id, setId] = useState(''); const [res, setRes] = useState(null); const [cfg, setCfg] = useState({ expectedInsertions: 50, falsePositiveProbability: 0.05 });

  const probe = () => run(async () => { setLiveRes(await api.post('/requests/screen', { requestId: liveId })); live.reload(); });
  const reset = () => run(async () => { await api.post('/bloom/sandbox/reset', { expectedInsertions: Number(cfg.expectedInsertions), falsePositiveProbability: Number(cfg.falsePositiveProbability) }); setRes(null); box.reload(); }, 'Sandbox filter rebuilt');
  const add = () => run(async () => { setRes(await api.post('/bloom/sandbox/add', { requestId: id })); box.reload(); });
  const query = () => run(async () => { setRes(await api.post('/bloom/sandbox/query', { requestId: id })); box.reload(); });
  const fill = (n) => run(async () => { for (let i = 0; i < n; i++) await api.post('/bloom/sandbox/add', { requestId: `ITEM-${Date.now()}-${i}` }); box.reload(); }, `${n} identifiers inserted`);
  const probeMarks = (r) => (r ? r.positions : []);

  return (
    <>
      <PageHeader title="Bloom filter intelligence" subtitle="A compact probabilistic set that screens incoming request identifiers. “Definitely absent” is certain; “possibly present” must be verified against the database." />
      <Note className="mb-6"><b>How to read it.</b> Each identifier sets k bits chosen by double hashing (h₁ + i·h₂ mod m). If any of its k bits is 0 the identifier was never added (no false negatives). If all are 1 it may be a duplicate <i>or</i> a coincidence caused by other items — a false positive — so the application always runs an exact database lookup before rejecting anything.</Note>
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title="Live request filter" hint="Screens every real team request" icon={Binary} />
          <Loadable state={live}>{(s) => (<div className="space-y-3 p-4">
            <Metrics s={s} /><Stats s={s.stats} />
            <BitGrid bits={s.bitString} marks={probeMarks(liveRes)} />
            <div className="flex gap-2"><input className="input font-mono" placeholder="Request id to probe" value={liveId} onChange={(e) => setLiveId(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && liveId.trim() && probe()} aria-label="Request id to probe" /><button className="btn-primary" disabled={!liveId.trim() || busy} onClick={probe}><Search size={15} />Probe</button></div>
            {liveRes && <Result r={liveRes} />}
            <p className="text-[11px] text-ink-400">Probing only reads the filter and the database; it never inserts. Teal cells are 1-bits; amber outlines are the probed identifier's hash positions.</p>
          </div>)}</Loadable>
        </Card>
        <Card>
          <CardHeader title="Sandbox filter" hint="Experiment freely — isolated from live data" icon={FlaskRound} />
          <Loadable state={box}>{(s) => (<div className="space-y-3 p-4">
            <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-2"><Field label="Expected items"><input type="number" min="1" max="5000" className="input" value={cfg.expectedInsertions} onChange={(e) => setCfg({ ...cfg, expectedInsertions: e.target.value })} /></Field><Field label="Target false-positive prob."><input type="number" step="0.01" min="0.0001" max="0.5" className="input" value={cfg.falsePositiveProbability} onChange={(e) => setCfg({ ...cfg, falsePositiveProbability: e.target.value })} /></Field><button className="btn-ghost" disabled={busy} onClick={reset}>Rebuild</button></div>
            <Metrics s={s} /><Stats s={s.stats} />
            <BitGrid bits={s.bitString} marks={probeMarks(res)} cell={9} />
            <div className="flex flex-wrap gap-2"><input className="input min-w-[180px] flex-1 font-mono" placeholder="identifier" value={id} onChange={(e) => setId(e.target.value)} aria-label="Sandbox identifier" /><button className="btn-ghost" disabled={!id.trim() || busy} onClick={add}><Plus size={15} />Add</button><button className="btn-primary" disabled={!id.trim() || busy} onClick={query}><Search size={15} />Query</button></div>
            <div className="flex gap-2"><button className="btn-ghost" disabled={busy} onClick={() => fill(10)}>Insert 10 random</button><button className="btn-ghost" disabled={busy} onClick={() => fill(40)}>Insert 40 random</button></div>
            {res && <Result r={res} />}
            <Note tone="warn">Try inserting more items than “Expected items”: occupancy rises and the false-positive rate climbs above target — but no false negatives ever appear.</Note>
          </div>)}</Loadable>
        </Card>
      </div>
    </>
  );
}
