/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eef4ff',
          100: '#d9e6ff',
          200: '#bcd2ff',
          300: '#8eb4ff',
          400: '#598bff',
          500: '#3366ff',
          600: '#0f62fe',
          700: '#0b50e0',
          800: '#0d42b5',
          900: '#123b8e',
          950: '#0f2556',
        },
        accent: {
          50: '#fff6ec',
          100: '#ffe9d3',
          200: '#ffcfa6',
          300: '#ffad6e',
          400: '#ff7f33',
          500: '#ff6b35',
          600: '#f0490a',
          700: '#c7350b',
          800: '#9e2c11',
          900: '#7f2812',
        },
        deep: {
          900: '#0a0a1a',
          800: '#1a1a2e',
          700: '#25253e',
          600: '#32324e',
        },
        surface: {
          DEFAULT: '#ffffff',
          muted: '#f5f6fa',
          border: '#e8ecf1',
        }
      },
      fontFamily: {
        display: ['"DM Serif Display"', 'Georgia', 'serif'],
        sans: ['"DM Sans"', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        'xl': '12px',
        '2xl': '16px',
      },
      boxShadow: {
        'card': '0 4px 24px rgba(0,0,0,0.06)',
        'card-hover': '0 8px 32px rgba(0,0,0,0.10)',
        'btn': '0 2px 8px rgba(15,98,254,0.25)',
      },
      animation: {
        'fade-in': 'fadeIn 0.5s ease-out',
        'slide-up': 'slideUp 0.5s ease-out',
        'wave': 'wave 1.5s ease-in-out infinite',
        'pulse-dot': 'pulseDot 2s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        wave: {
          '0%, 100%': { transform: 'scaleY(1)' },
          '50%': { transform: 'scaleY(0.4)' },
        },
        pulseDot: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.4' },
        },
      },
    },
  },
  plugins: [],
}
