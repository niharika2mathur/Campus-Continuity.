import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import FlowGraph from './FlowGraph.jsx';
import { frameState } from '../lib/util.js';
import trap from '../test-fixtures/greedy-trap.trace.json';
import mini from '../test-fixtures/mini-campus.trace.json';

// Fixtures are REAL Edmonds–Karp traces serialised by backend/tools/TraceFixture.java from the Java engine.
const draw = (fx, frame, phase = 'done', extra = {}) => {
  const o = fx.outcome;
  return render(<FlowGraph graph={o.graph} steps={o.steps} terminal={o.terminal} frame={frame} phase={phase} visitedCount={0} showIsolated={false} showResidual {...extra} />);
};

describe('FlowGraph rendering of a real backend trace (greedy trap)', () => {
  const o = trap.outcome;
  it('trace is consistent: bottlenecks sum to max flow', () => {
    expect(o.steps.reduce((s, x) => s + x.bottleneck, 0)).toBe(o.terminal.maxFlow);
    expect(o.terminal.maxFlow).toBe(2);
  });
  it('at least one augmenting path uses a reverse residual arc (re-routing)', () => {
    expect(o.steps.some((s) => s.pathReverse.some(Boolean))).toBe(true);
  });
  it('renders one SVG with a labelled node per team and slot', () => {
    const { container, getByText } = draw(trap, 0);
    expect(container.querySelector('svg')).toBeTruthy();
    expect(getByText('FLEX')).toBeTruthy(); expect(getByText('RIGID')).toBeTruthy();
  });
  it('augmenting path is highlighted with the backend path length', () => {
    const f = o.steps.length;
    const { container } = draw(trap, f, 'path');
    const lastPath = o.steps[f - 1].pathEdges.length;
    expect(container.querySelectorAll('line.flow-dash').length).toBe(lastPath);
  });
  it('final frame draws exactly the backend min-cut edges (dashed red) and reports flow value', () => {
    const { container } = draw(trap, o.steps.length + 1);
    const cutLines = [...container.querySelectorAll('line')].filter((l) => l.getAttribute('stroke') === '#f87171' && l.getAttribute('stroke-dasharray'));
    expect(cutLines.length).toBe(o.terminal.cutEdgeIds.length);
    expect(frameState(o.graph, o.steps, o.terminal, o.steps.length + 1).flowValue).toBe(2);
  });
});

describe('mini campus real trace', () => {
  const o = mini.outcome;
  it('flags the incompatible team and keeps flow conservation visible in the final frame', () => {
    expect(o.unallocated.some((u) => u.team === 'X1' && u.category === 'NO_COMPATIBLE_SLOT')).toBe(true);
    const { container } = draw(mini, o.steps.length + 1);
    expect(container.querySelectorAll('circle').length).toBeGreaterThan(5);
  });
});
