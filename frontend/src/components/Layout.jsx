import { useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Activity, BarChart3, Binary, ClipboardList, Database, FileText, FlaskConical, FlaskRound, GitBranch, LayoutDashboard, LogOut, Menu, Settings, Users, X, Boxes, Waypoints } from 'lucide-react';
import { useApi } from '../lib/hooks.js';
import { api } from '../api.js';

export const NAV = [
  { group: 'Operate', items: [
    { to: '/', label: 'Overview', icon: LayoutDashboard, end: true }, { to: '/resources', label: 'Resource inventory', icon: Boxes },
    { to: '/teams', label: 'Student teams', icon: Users }, { to: '/requests', label: 'Request management', icon: ClipboardList } ] },
  { group: 'Decide', items: [
    { to: '/allocation', label: 'Allocation studio', icon: Waypoints }, { to: '/simulator', label: 'What-if simulator', icon: FlaskConical } ] },
  { group: 'Understand', items: [
    { to: '/observatory', label: 'Algorithm observatory', icon: GitBranch }, { to: '/bloom', label: 'Bloom filter intelligence', icon: Binary },
    { to: '/benchmarks', label: 'Performance & benchmarks', icon: BarChart3 } ] },
  { group: 'Govern', items: [
    { to: '/reports', label: 'Reports & history', icon: FileText }, { to: '/settings', label: 'Settings & data', icon: Settings } ] },
];
const flat = NAV.flatMap((g) => g.items);

export default function Layout() {
  const [open, setOpen] = useState(false);
  const loc = useLocation(); const nav = useNavigate();
  const health = useApi(() => api.get('/admin/health'), [loc.pathname]);
  const current = flat.find((i) => (i.end ? loc.pathname === i.to : loc.pathname.startsWith(i.to))) || flat[0];
  const user = JSON.parse(localStorage.getItem('cc-user') || '{}');
  const up = !!health.data;

  const sidebar = (
    <nav className="flex h-full flex-col" aria-label="Main navigation">
      <div className="flex items-center gap-2.5 px-4 py-4">
        <div className="grid h-8 w-8 place-items-center rounded-lg border border-ink-600 bg-ink-800"><Activity size={16} className="text-teal-400" /></div>
        <div><div className="text-sm font-semibold leading-tight text-white">Campus Continuity</div><div className="text-[11px] text-ink-400">Recovery &amp; allocation</div></div>
      </div>
      <div className="flex-1 overflow-y-auto px-2 pb-4">
        {NAV.map((g) => (
          <div key={g.group} className="mb-3">
            <div className="px-3 pb-1 pt-2 text-[10px] font-semibold uppercase tracking-widest text-ink-400">{g.group}</div>
            {g.items.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}
                className={({ isActive }) => `mb-0.5 flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors ${isActive ? 'bg-brand-500/15 text-white ring-1 ring-brand-500/30' : 'text-ink-300 hover:bg-ink-800 hover:text-white'}`}>
                <Icon size={16} />{label}
              </NavLink>
            ))}
          </div>
        ))}
      </div>
      <div className="border-t border-ink-700 p-3 text-[11px] text-ink-400">Java 21 · Spring Boot · PostgreSQL<br />Algorithms run on the server.</div>
    </nav>
  );

  return (
    <div className="flex h-full bg-ink-900">
      <aside className="hidden w-64 shrink-0 border-r border-ink-700 bg-ink-950 lg:block">{sidebar}</aside>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden"><div className="absolute inset-0 bg-black/60" onClick={() => setOpen(false)} />
          <aside className="fade-in absolute left-0 top-0 h-full w-72 border-r border-ink-700 bg-ink-950"><button className="absolute right-3 top-3 rounded p-1 text-ink-300" onClick={() => setOpen(false)} aria-label="Close menu"><X size={18} /></button>{sidebar}</aside></div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-ink-700 bg-ink-900/90 px-4 backdrop-blur">
          <div className="flex min-w-0 items-center gap-3">
            <button className="rounded-lg p-1.5 text-ink-200 hover:bg-ink-800 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
            <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 text-sm"><span className="text-ink-400">Campus Continuity</span><span className="text-ink-500">/</span><span className="truncate font-medium text-white">{current.label}</span></nav>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden items-center gap-1.5 rounded-full border border-ink-600 bg-ink-800 px-2.5 py-1 text-[11px] text-ink-200 sm:flex" title={up ? 'Backend reachable' : 'Backend not reachable'}>
              <span className={`h-1.5 w-1.5 rounded-full ${up ? 'bg-teal-400' : 'bg-red-400'}`} />{up ? 'Backend online' : 'Backend offline'}
            </span>
            <span className="hidden text-xs text-ink-300 md:block">{user.name || 'Demo admin'}</span>
            <button className="rounded-lg p-1.5 text-ink-300 hover:bg-ink-800 hover:text-white" onClick={() => { localStorage.removeItem('cc-user'); nav('/login'); }} aria-label="Sign out" title="Sign out"><LogOut size={16} /></button>
          </div>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto"><div className="fade-in mx-auto max-w-[1400px] p-4 sm:p-6" key={loc.pathname}><Outlet /></div></main>
      </div>
    </div>
  );
}
