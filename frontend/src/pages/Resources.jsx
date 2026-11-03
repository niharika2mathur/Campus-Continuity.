import { useState } from 'react';
import { Pencil, Plus, RotateCcw, ShieldAlert } from 'lucide-react';
import { api } from '../api.js';
import { useApi, useAction } from '../lib/hooks.js';
import { Badge, Card, Empty, Field, Loadable, Modal, PageHeader, StatusBadge, Table, Tabs, THead, useToast } from '../components/ui.jsx';

const blankLab = { code: '', name: '', building: '', capacity: 2, maxTeamSize: 6, equipment: '' };
const blankSlot = { code: '', label: '', dayOfWeek: '', startTime: '', durationMinutes: 120 };
const tagList = (s) => s.split(/[,;]/).map((x) => x.trim()).filter(Boolean);

export default function Resources() {
  const [tab, setTab] = useState('labs');
  const labs = useApi(() => api.get('/labs')); const slots = useApi(() => api.get('/slots'));
  const toast = useToast(); const [run, busy] = useAction(toast);
  const [edit, setEdit] = useState(null);      // {kind, id?, form}
  const [status, setStatus] = useState(null);  // {kind, item, status, reason}
  const [errs, setErrs] = useState({});

  const save = async () => {
    setErrs({});
    const { kind, id, form } = edit;
    const path = kind === 'lab' ? '/labs' : '/slots';
    const body = kind === 'lab' ? { ...form, capacity: Number(form.capacity), maxTeamSize: Number(form.maxTeamSize), equipment: Array.isArray(form.equipment) ? form.equipment : tagList(form.equipment) }
      : { ...form, durationMinutes: Number(form.durationMinutes) };
    try {
      if (id) await api.put(`${path}/${id}`, body); else await api.post(path, body);
      toast.ok(`${kind === 'lab' ? 'Laboratory' : 'Time slot'} saved`); setEdit(null); (kind === 'lab' ? labs : slots).reload();
    } catch (e) { setErrs(e.fieldErrors || {}); toast.err(e.message); }
  };
  const setSt = (kind, item, st, reason) => run(async () => { await api.patch(`/${kind}s/${item.id}/status`, { status: st, reason }); (kind === 'lab' ? labs : slots).reload(); setStatus(null); }, `${item.code} is now ${st}`);
  const f = (k) => (e) => setEdit({ ...edit, form: { ...edit.form, [k]: e.target.value } });

  return (
    <>
      <PageHeader title="Resource inventory" subtitle="Laboratories, their capacities and equipment, and the time slots they can be booked in. Disrupting a resource immediately changes the next allocation."
        actions={<button className="btn-primary" onClick={() => setEdit(tab === 'labs' ? { kind: 'lab', form: blankLab } : { kind: 'slot', form: blankSlot })}><Plus size={16} />Add {tab === 'labs' ? 'laboratory' : 'time slot'}</button>} />
      <Tabs tabs={[{ id: 'labs', label: 'Laboratories' }, { id: 'slots', label: 'Time slots' }]} value={tab} onChange={setTab} />
      {tab === 'labs' ? (
        <Card><Loadable state={labs}>{(rows) => rows.length === 0 ? <Empty title="No laboratories" text="Add one or load the sample data from Settings." /> : (
          <Table><THead cols={['Code', 'Name', 'Capacity', 'Max team', 'Equipment & software', 'Status', '']} /><tbody>{rows.map((l) => (
            <tr key={l.id} className="border-b border-ink-700/60 hover:bg-ink-800/50">
              <td className="td font-mono text-xs">{l.code}</td><td className="td">{l.name}<div className="text-xs text-ink-400">{l.building}</div></td>
              <td className="td tabular-nums" title="Teams that can use each time slot simultaneously">{l.capacity}</td><td className="td tabular-nums">{l.maxTeamSize}</td>
              <td className="td"><div className="flex flex-wrap gap-1">{l.equipment.length ? l.equipment.map((e) => <Badge key={e}>{e}</Badge>) : <span className="text-xs text-ink-400">none</span>}</div></td>
              <td className="td"><StatusBadge status={l.status} />{l.reason && <div className="mt-1 max-w-[200px] truncate text-[11px] text-ink-400" title={l.reason}>{l.reason}</div>}</td>
              <td className="td"><div className="flex justify-end gap-1.5">
                <button className="btn-ghost !px-2" aria-label={`Edit ${l.code}`} onClick={() => setEdit({ kind: 'lab', id: l.id, form: { ...l, equipment: l.equipment.join(', ') } })}><Pencil size={14} /></button>
                {l.status === 'AVAILABLE' ? <button className="btn-ghost !px-2" aria-label={`Mark ${l.code} unavailable`} onClick={() => setStatus({ kind: 'lab', item: l, status: 'DISRUPTED', reason: '' })}><ShieldAlert size={14} />Disrupt</button>
                  : <button className="btn-ghost !px-2" onClick={() => setSt('lab', l, 'AVAILABLE')}><RotateCcw size={14} />Restore</button>}</div></td></tr>))}</tbody></Table>)}</Loadable></Card>
      ) : (
        <Card><Loadable state={slots}>{(rows) => rows.length === 0 ? <Empty title="No time slots" /> : (
          <Table><THead cols={['Code', 'Label', 'Day / start', 'Duration', 'Status', '']} /><tbody>{rows.map((s) => (
            <tr key={s.id} className="border-b border-ink-700/60 hover:bg-ink-800/50"><td className="td font-mono text-xs">{s.code}</td><td className="td">{s.label}</td><td className="td text-ink-300">{[s.dayOfWeek, s.startTime].filter(Boolean).join(' ') || '—'}</td><td className="td tabular-nums">{s.durationMinutes} min</td>
              <td className="td"><StatusBadge status={s.status} />{s.reason && <div className="mt-1 text-[11px] text-ink-400">{s.reason}</div>}</td>
              <td className="td"><div className="flex justify-end gap-1.5"><button className="btn-ghost !px-2" aria-label={`Edit ${s.code}`} onClick={() => setEdit({ kind: 'slot', id: s.id, form: { ...s } })}><Pencil size={14} /></button>
                {s.status === 'AVAILABLE' ? <button className="btn-ghost !px-2" onClick={() => setStatus({ kind: 'slot', item: s, status: 'DISRUPTED', reason: '' })}><ShieldAlert size={14} />Disable</button> : <button className="btn-ghost !px-2" onClick={() => setSt('slot', s, 'AVAILABLE')}><RotateCcw size={14} />Restore</button>}</div></td></tr>))}</tbody></Table>)}</Loadable></Card>
      )}

      {edit && (
        <Modal title={`${edit.id ? 'Edit' : 'Add'} ${edit.kind === 'lab' ? 'laboratory' : 'time slot'}`} onClose={() => setEdit(null)} footer={<><button className="btn-ghost" onClick={() => setEdit(null)}>Cancel</button><button className="btn-primary" disabled={busy} onClick={save}>Save</button></>}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Code" error={errs.code}><input className="input" value={edit.form.code} onChange={f('code')} /></Field>
            {edit.kind === 'lab' ? <>
              <Field label="Name" error={errs.name}><input className="input" value={edit.form.name} onChange={f('name')} /></Field>
              <Field label="Building"><input className="input" value={edit.form.building || ''} onChange={f('building')} /></Field>
              <Field label="Capacity (teams per slot)" error={errs.capacity} hint="Zero is allowed; negatives are rejected."><input type="number" min="0" className="input" value={edit.form.capacity} onChange={f('capacity')} /></Field>
              <Field label="Max team size" error={errs.maxTeamSize}><input type="number" min="1" className="input" value={edit.form.maxTeamSize} onChange={f('maxTeamSize')} /></Field>
              <div className="sm:col-span-2"><Field label="Equipment & software" hint="Comma-separated, e.g. gpu, linux, matlab"><input className="input" value={edit.form.equipment} onChange={f('equipment')} /></Field></div>
            </> : <>
              <Field label="Label" error={errs.label}><input className="input" value={edit.form.label} onChange={f('label')} /></Field>
              <Field label="Day"><input className="input" placeholder="MON" value={edit.form.dayOfWeek || ''} onChange={f('dayOfWeek')} /></Field>
              <Field label="Start"><input className="input" placeholder="09:00" value={edit.form.startTime || ''} onChange={f('startTime')} /></Field>
              <Field label="Duration (minutes)" error={errs.durationMinutes}><input type="number" min="1" className="input" value={edit.form.durationMinutes} onChange={f('durationMinutes')} /></Field></>}
          </div>
        </Modal>)}
      {status && (
        <Modal title={`Mark ${status.item.code} unavailable`} onClose={() => setStatus(null)} footer={<><button className="btn-ghost" onClick={() => setStatus(null)}>Cancel</button><button className="btn-primary" disabled={busy} onClick={() => setSt(status.kind, status.item, status.status, status.reason)}>Apply</button></>}>
          <div className="space-y-3">
            <Field label="Status"><select className="input" value={status.status} onChange={(e) => setStatus({ ...status, status: e.target.value })}><option value="DISRUPTED">Disrupted (temporary — can be restored)</option><option value="DEACTIVATED">Deactivated (retired)</option></select></Field>
            <Field label="Disruption reason" hint="Shown on the dashboard and in reports."><input className="input" value={status.reason} onChange={(e) => setStatus({ ...status, reason: e.target.value })} placeholder="e.g. Cooling failure" /></Field>
          </div>
        </Modal>)}
    </>
  );
}
