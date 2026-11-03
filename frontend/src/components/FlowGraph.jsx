import { useMemo } from 'react';
import { frameState, layoutGraph } from '../lib/util.js';

const COLORS = { base: '#2a3858', flow: '#2dd4bf', path: '#60a5fa', rev: '#fbbf24', cut: '#f87171', node: '#131c31', text: '#c3cde0' };

/**
 * Pure SVG renderer. Every state shown (flows, BFS visit order, augmenting path, bottleneck, min cut) is read from the
 * backend's execution trace via frameState(); this component never computes flow or paths itself.
 */
export default function FlowGraph({ graph, steps, terminal, frame, phase, visitedCount, showIsolated, showResidual }) {
  const layout = useMemo(() => layoutGraph(graph, { hideIsolated: !showIsolated }), [graph, showIsolated]);
  const st = frameState(graph, steps, terminal, frame);
  const flows = st.kind === 'augment' && phase === 'bfs' ? st.prevFlows : st.flows;
  const visitOrder = st.kind === 'augment' ? st.step.visitOrder : st.kind === 'final' ? terminal.visitOrder : [];
  const visited = new Set(visitOrder.slice(0, st.kind === 'augment' && phase === 'bfs' ? visitedCount : visitOrder.length));
  const current = st.kind === 'augment' && phase === 'bfs' && visitedCount > 0 ? visitOrder[Math.min(visitedCount, visitOrder.length) - 1] : null;
  const showPath = st.kind === 'augment' && phase !== 'bfs';
  const pathEdges = new Set(showPath ? st.step.pathEdges : []);
  const pathNodes = new Set(showPath ? st.step.pathNodes : []);
  const revEdges = new Set(showPath ? st.step.pathEdges.filter((_, i) => st.step.pathReverse[i]) : []);
  const sideS = st.kind === 'final' ? new Set(terminal.sourceSide) : null;
  const cut = st.kind === 'final' ? new Set(terminal.cutEdgeIds) : new Set();
  const { pos, width, height } = layout;

  const R = (n) => (n.type === 'SOURCE' || n.type === 'SINK' ? 16 : n.type === 'TEAM' ? 6 : 6);
  const edgeEls = graph.edges.map((e) => {
    const a = pos[e.from], b = pos[e.to];
    if (!a || !b) return null;
    const f = flows[e.id] || 0;
    const onPath = pathEdges.has(e.id), isCut = cut.has(e.id);
    const dx = b.x - a.x, dy = b.y - a.y, len = Math.hypot(dx, dy) || 1;
    const ux = dx / len, uy = dy / len;
    const ra = R(graph.nodes[e.from]) + 1, rb = R(graph.nodes[e.to]) + 4;
    const x1 = a.x + ux * ra, y1 = a.y + uy * ra, x2 = b.x - ux * rb, y2 = b.y - uy * rb;
    const color = isCut ? COLORS.cut : onPath ? (revEdges.has(e.id) ? COLORS.rev : COLORS.path) : f > 0 ? COLORS.flow : COLORS.base;
    const mk = isCut ? 'cut' : onPath ? (revEdges.has(e.id) ? 'rev' : 'path') : f > 0 ? 'flow' : 'base';
    const label = f > 0 || onPath || isCut || e.to === graph.sink;
    const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
    const nx = -uy * 5, ny = ux * 5; // perpendicular offset for the residual (reverse) arc
    return (
      <g key={e.id}>
        <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={color} strokeWidth={onPath ? 3 : f > 0 ? 2 : 1} strokeOpacity={f > 0 || onPath || isCut ? 1 : 0.55}
          strokeDasharray={isCut ? '5 4' : undefined} className={onPath ? 'flow-dash' : undefined} markerEnd={`url(#m-${mk})`} />
        {showResidual && f > 0 && <line x1={x2 + nx} y1={y2 + ny} x2={x1 + nx} y2={y1 + ny} stroke={COLORS.rev} strokeWidth={1} strokeDasharray="3 3" strokeOpacity={0.7} markerEnd="url(#m-rev)" />}
        {label && <text x={mx + 4} y={my - 4} fontSize={9} fill={isCut ? COLORS.cut : color} className="font-mono" textAnchor="middle" style={{ paintOrder: 'stroke', stroke: '#0b1120', strokeWidth: 3 }}>{`${f}/${e.capacity}`}</text>}
      </g>
    );
  });

  const nodeEls = graph.nodes.map((n) => {
    const p = pos[n.id];
    if (!p) return null;
    const isV = visited.has(n.id), isCur = current === n.id, onP = pathNodes.has(n.id), inS = sideS?.has(n.id);
    const stroke = onP ? COLORS.path : isCur ? '#ffffff' : inS ? '#3b82f6' : isV ? '#3b82f6' : '#3a4968';
    const fill = onP ? '#1e3a8a' : inS ? '#16284f' : isV ? '#172554' : COLORS.node;
    if (n.type === 'SOURCE' || n.type === 'SINK') return (
      <g key={n.id}><rect x={p.x - 16} y={p.y - 16} width={32} height={32} rx={8} fill={fill} stroke={n.type === 'SOURCE' ? '#3b82f6' : '#2dd4bf'} strokeWidth={2} />
        <text x={p.x} y={p.y + 4} textAnchor="middle" fontSize={11} fontWeight="600" fill="#fff">{n.type === 'SOURCE' ? 's' : 't'}</text>
        <text x={p.x} y={p.y + 30} textAnchor="middle" fontSize={10} fill={COLORS.text}>{n.type === 'SOURCE' ? 'Source' : 'Sink'}</text></g>);
    return (
      <g key={n.id}><circle cx={p.x} cy={p.y} r={6} fill={fill} stroke={stroke} strokeWidth={isCur || onP ? 2.5 : 1.5} />
        <text x={n.type === 'TEAM' ? p.x - 11 : p.x + 11} y={p.y + 3.5} fontSize={10} textAnchor={n.type === 'TEAM' ? 'end' : 'start'} fill={COLORS.text}>{n.label}</text></g>);
  });

  return (
    <div className="overflow-auto rounded-lg border border-ink-700 bg-ink-950">
      <svg viewBox={`0 0 ${width} ${height}`} className="min-w-[760px] w-full" style={{ height: Math.min(Math.max(height, 300), 760) }} role="img" aria-label="Flow network visualisation">
        <defs>{Object.entries({ base: COLORS.base, flow: COLORS.flow, path: COLORS.path, rev: COLORS.rev, cut: COLORS.cut }).map(([k, c]) => (
          <marker key={k} id={`m-${k}`} viewBox="0 0 8 8" refX="7" refY="4" markerWidth="5" markerHeight="5" orient="auto"><path d="M0 0 8 4 0 8z" fill={c} /></marker>))}</defs>
        <text x={300} y={18} textAnchor="middle" fontSize={10} fill="#6b7a99" letterSpacing="1.5">TEAMS (supply 1)</text>
        <text x={640} y={18} textAnchor="middle" fontSize={10} fill="#6b7a99" letterSpacing="1.5">LAB / SLOT (capacity)</text>
        {edgeEls}{nodeEls}
      </svg>
    </div>
  );
}
