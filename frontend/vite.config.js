import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Must match eventhive.frontend-url (CORS origin + Stripe redirects)
  server: { port: 5173, strictPort: true },
})
