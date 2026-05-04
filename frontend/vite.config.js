import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // utile uniquement pour `vite dev` en local hors Docker
      '/api': 'http://127.0.0.1'
    }
  }
})
