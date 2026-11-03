/** Design tokens: midnight navy surfaces, restrained electric-blue + teal accents. */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: { 950: '#070b16', 900: '#0b1120', 850: '#0f172a', 800: '#131c31', 700: '#1b2640', 600: '#26334f', 500: '#3a4968', 400: '#6b7a99', 300: '#94a3bd', 200: '#c3cde0', 100: '#e5eaf3' },
        brand: { 400: '#60a5fa', 500: '#3b82f6', 600: '#2563eb' },
        teal: { 300: '#5eead4', 400: '#2dd4bf', 500: '#14b8a6' },
      },
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'], mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'] },
      boxShadow: { card: '0 1px 0 0 rgba(255,255,255,0.03) inset, 0 8px 24px -12px rgba(0,0,0,0.6)' },
    },
  },
  plugins: [],
};
