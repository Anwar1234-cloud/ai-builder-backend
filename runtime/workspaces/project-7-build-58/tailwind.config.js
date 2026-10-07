export default {
  tailwind: {
    config: {
      content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
      theme: {
        extend: {
          colors: {
            charcoal: {
              DEFAULT: '#121212',
              dark: '#0A0A0A',
              light: '#1E1C18',
              card: '#181715',
              border: '#2D2A26',
            },
            beige: {
              DEFAULT: '#EAE6DF',
              light: '#F4F1EC',
              dark: '#C8C2B6',
              muted: '#A39E93',
            },
            gold: {
              DEFAULT: '#C5A059',
              light: '#DFBF77',
              dark: '#A6823F',
              subtle: 'rgba(197, 160, 89, 0.15)',
            }
          },
          fontFamily: {
            serif: ['Cormorant Garamond', 'serif', 'Georgia'],
            sans: ['Plus Jakarta Sans', 'sans-serif', 'system-ui'],
          },
          boxShadow: {
            'luxury': '0 20px 40px -15px rgba(0, 0, 0, 0.5), 0 0 20px 0 rgba(197, 160, 89, 0.08)',
            'luxury-hover': '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 30px 0 rgba(197, 160, 89, 0.18)',
          }
        },
      },
    },
  },
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        charcoal: {
          DEFAULT: '#121212',
          dark: '#0A0A0A',
          light: '#1E1C18',
          card: '#181715',
          border: '#2D2A26',
        },
        beige: {
          DEFAULT: '#EAE6DF',
          light: '#F4F1EC',
          dark: '#C8C2B6',
          muted: '#A39E93',
        },
        gold: {
          DEFAULT: '#C5A059',
          light: '#DFBF77',
          dark: '#A6823F',
          subtle: 'rgba(197, 160, 89, 0.15)',
        }
      },
      fontFamily: {
        serif: ['Cormorant Garamond', 'serif', 'Georgia'],
        sans: ['Plus Jakarta Sans', 'sans-serif', 'system-ui'],
      },
      boxShadow: {
        'luxury': '0 20px 40px -15px rgba(0, 0, 0, 0.5), 0 0 20px 0 rgba(197, 160, 89, 0.08)',
        'luxury-hover': '0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 30px 0 rgba(197, 160, 89, 0.18)',
      }
    },
  },
  plugins: [],
};
