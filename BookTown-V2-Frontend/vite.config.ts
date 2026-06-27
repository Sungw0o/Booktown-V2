import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss()
  ],
  base: '/',
  build: {
    // 프로덕션 빌드 시 sourcemap 비활성화 (보안 + 빌드 크기)
    sourcemap: false,
  },
  // 로컬 개발 시 VITE_API_BASE_URL 환경변수로 API 서버 지정
  // 예: VITE_API_BASE_URL=http://localhost:8080 npm run dev
})
