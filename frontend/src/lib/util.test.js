import { describe, it, expect } from 'vitest';
import { toCsv, layoutGraph, frameState, residualReverse, pct, fmtMs } from './util.js';

const graph = {
  source: 0, sink: 4,
  nodes: [{ id: 0, type: 'SOURCE' }, { id: 1, type: 'TEAM', label: 'A' }, { id: 2, type: 'SLOT', label: 'L/S1' }, { id: 3, type: 'SLOT', label: 'L/S2' }, { id: 4, type: 'SINK' }],
  edges: [{ id: 0, from: 0, to: 1, capacity: 1 }, { id: 1, from: 1, to: 2, capacity: 1 }, { id: 2, from: 2, to: 4, capacity: 1 }],
};

describe('toCsv', () => {
  it('quotes commas, quotes and newlines', () => { expect(toCsv([{ a: 'x,y', b: 'say "hi"' }])).toBe('a,b\n"x,y","say ""hi"""'); });
  it('neutralises formula injection', () => { expect(toCsv([{ a: '=SUM(A1)' }])).toBe("a\n'=SUM(A1)"); });
  it('joins arrays and handles null', () => { expect(toCsv([{ a: ['x', 'y'], b: null }])).toBe('a,b\nx;y,'); });
  it('empty rows with explicit columns gives header only', () => { expect(toCsv([], ['a', 'b'])).toBe('a,b'); });
});

describe('layoutGraph', () => {
  it('hides slot nodes with no incoming team edge', () => {
    const l = layoutGraph(graph);
    expect(l.hidden.has(3)).toBe(true);
    expect(l.pos[2]).toBeDefined();
    expect(l.pos[3]).toBeUndefined();
  });
  it('can show isolated nodes', () => { expect(layoutGraph(graph, { hideIsolated: false }).pos[3]).toBeDefined(); });
});

describe('frameState', () => {
  const steps = [{ iteration: 1, edgeFlows: [1, 1, 1], flowValue: 1 }];
  const terminal = { edgeFlows: [1, 1, 1], maxFlow: 1 };
  it('frame 0 is the empty initial network', () => { expect(frameState(graph, steps, terminal, 0).flows).toEqual([0, 0, 0]); });
  it('frame k reads the backend step', () => {
    const f = frameState(graph, steps, terminal, 1);
    expect(f.kind).toBe('augment'); expect(f.flowValue).toBe(1); expect(f.prevFlows).toEqual([0, 0, 0]);
  });
  it('last frame is terminal', () => { expect(frameState(graph, steps, terminal, 2).kind).toBe('final'); });
});

describe('misc', () => {
  it('residualReverse lists edges with flow', () => { expect(residualReverse([0, 2, 0, 1])).toEqual([1, 3]); });
  it('formats', () => { expect(pct(0.1234)).toBe('12.3%'); expect(fmtMs(0.5)).toBe('0.500 ms'); expect(fmtMs(2500)).toBe('2.50 s'); });
});
