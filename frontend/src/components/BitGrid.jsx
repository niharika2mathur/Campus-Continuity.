import { useEffect, useRef } from 'react';

/** Canvas bit-array view. `bits` is the string of 0/1 returned by the Java BloomFilter; `marks` are hash positions to highlight. */
export default function BitGrid({ bits, marks = [], markColor = '#fbbf24', cell = 6 }) {
  const ref = useRef(null);
  const m = bits.length;
  const cols = Math.max(16, Math.ceil(Math.sqrt(m * 2.2)));
  const rows = Math.ceil(m / cols);
  const step = cell + 1;
  useEffect(() => {
    const c = ref.current; if (!c) return;
    const ctx = c.getContext('2d');
    ctx.clearRect(0, 0, c.width, c.height);
    for (let i = 0; i < m; i++) {
      ctx.fillStyle = bits[i] === '1' ? '#2dd4bf' : '#1b2640';
      ctx.fillRect((i % cols) * step, Math.floor(i / cols) * step, cell, cell);
    }
    ctx.lineWidth = 1.5; ctx.strokeStyle = markColor;
    marks.forEach((p) => { const x = (p % cols) * step, y = Math.floor(p / cols) * step; ctx.strokeRect(x - 1.5, y - 1.5, cell + 3, cell + 3); });
  }, [bits, marks, cols, m, step, cell, markColor]);
  return <div className="overflow-auto rounded-lg border border-ink-700 bg-ink-950 p-2"><canvas ref={ref} width={cols * step} height={rows * step} role="img" aria-label={`Bloom filter bit array with ${m} bits`} /></div>;
}
